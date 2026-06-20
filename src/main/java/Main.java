import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

public class Main {

    static class BackgroundJob {
        int jobNumber;
        Process process;
        String originalCommand;

        BackgroundJob(int jobNumber, Process process, String originalCommand) {
            this.jobNumber = jobNumber;
            this.process = process;
            this.originalCommand = originalCommand;
        }
    }

    private static final List<BackgroundJob> activeJobs = new ArrayList<>();
    private static int nextJobNumber = 1;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            // Check and notify finished background jobs BEFORE displaying the prompt
            checkCompletedJobs();

            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }
            
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            if (input.equals("exit")) {
                break;
            }

            // Handle pipelines first
            if (input.contains("|")) {
                PipelineHandler.executePipeline(input);
            } else {
                executeSingleCommand(input);
            }
        }
        scanner.close();
    }

    private static void checkCompletedJobs() {
        Iterator<BackgroundJob> iterator = activeJobs.iterator();
        while (iterator.hasNext()) {
            BackgroundJob job = iterator.next();
            if (!job.process.isAlive()) {
                System.out.printf("[%d]+  Done                 %s%n", job.jobNumber, job.originalCommand);
                iterator.remove();
                
                if (activeJobs.isEmpty()) {
                    nextJobNumber = 1;
                }
            }
        }
    }

    private static void executeSingleCommand(String input) {
        List<String> args = parseCommand(input);
        if (args.isEmpty()) return;

        // Built-in 'jobs' command handling
        if (args.get(0).equals("jobs")) {
            for (BackgroundJob job : activeJobs) {
                if (job.process.isAlive()) {
                    System.out.printf("[%d]+  Running              %s &%n", job.jobNumber, job.originalCommand);
                }
            }
            return;
        }

        boolean isBackground = false;
        if (args.get(args.size() - 1).equals("&")) {
            isBackground = true;
            args.remove(args.size() - 1); 
        }

        String commandString = String.join(" ", args);

        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.inheritIO(); 
            Process p = pb.start();

            if (isBackground) {
                System.out.println("[" + nextJobNumber + "] " + p.pid());
                activeJobs.add(new BackgroundJob(nextJobNumber, p, commandString));
                nextJobNumber++; 
            } else {
                p.waitFor();
            }
        } catch (Exception e) {
            System.out.println(args.get(0) + ": command not found");
        }
    }

    private static List<String> parseCommand(String command) {
        return new ArrayList<>(Arrays.asList(command.split("\\s+")));
    }

    // Pipeline Handler Nested Class
    public static class PipelineHandler {
        public static void executePipeline(String userInput) {
            String[] pipeParts = userInput.split("\\|");
            if (pipeParts.length != 2) {
                System.err.println("This stage only supports two-stage pipelines.");
                return;
            }

            List<String> cmd1Args = parseCommand(pipeParts[0].trim());
            List<String> cmd2Args = parseCommand(pipeParts[1].trim());

            try {
                ProcessBuilder pb1 = new ProcessBuilder(cmd1Args);
                pb1.redirectError(ProcessBuilder.Redirect.INHERIT); 
                Process p1 = pb1.start();

                ProcessBuilder pb2 = new ProcessBuilder(cmd2Args);
                pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
                pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT); 
                Process p2 = pb2.start();

                Thread pipeThread = new Thread(() -> {
                    try (InputStream inputFromP1 = p1.getInputStream();
                         OutputStream outputToP2 = p2.getOutputStream()) {
                        
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputFromP1.read(buffer)) != -1) {
                            outputToP2.write(buffer, 0, bytesRead);
                            outputToP2.flush(); 
                        }
                    } catch (Exception e) {
                        // Suppress broken pipes
                    }
                });
                pipeThread.start();

                p1.waitFor();
                pipeThread.join(); 
                p2.getOutputStream().close(); 
                p2.waitFor();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}