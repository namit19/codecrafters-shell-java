import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Standard REPL loop for a shell
        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) {
                break;
            }
            
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            // Exit command
            if (input.equals("exit")) {
                break;
            }

            // Check if the user input contains a pipeline operator
            if (input.contains("|")) {
                PipelineHandler.executePipeline(input);
            } else {
                // Handle regular single commands
                executeSingleCommand(input);
            }
        }
        scanner.close();
    }

    private static void executeSingleCommand(String input) {
        List<String> args = parseCommand(input);
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.inheritIO(); // Directly link to standard input/output/error
            Process p = pb.start();
            p.waitFor();
        } catch (Exception e) {
            System.out.println(args.get(0) +": command not found");
        }
    }

    // Helper method to split command strings by spaces
    private static List<String> parseCommand(String command) {
        return new ArrayList<>(Arrays.asList(command.split("\\s+")));
    }

    // Pipeline handler nested safely inside Main
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
                // 1. Start the first process (e.g., tail -f)
                ProcessBuilder pb1 = new ProcessBuilder(cmd1Args);
                pb1.redirectError(ProcessBuilder.Redirect.INHERIT); 
                Process p1 = pb1.start();

                // 2. Start the second process (e.g., head -n 5)
                ProcessBuilder pb2 = new ProcessBuilder(cmd2Args);
                pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
                pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT); // Output goes to terminal
                Process p2 = pb2.start();

                // 3. Background thread to pump bytes from P1 to P2 continuously
                Thread pipeThread = new Thread(() -> {
                    try (InputStream inputFromP1 = p1.getInputStream();
                         OutputStream outputToP2 = p2.getOutputStream()) {
                        
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputFromP1.read(buffer)) != -1) {
                            outputToP2.write(buffer, 0, bytesRead);
                            outputToP2.flush(); // Forces data out instantly for real-time streams
                        }
                    } catch (Exception e) {
                        // Subside broken pipe exceptions (e.g., when 'head' stops reading early)
                    }
                });
                pipeThread.start();

                // 4. Wait for P1 to exit or finish tracking
                p1.waitFor();
                
                // 5. Cleanup streams after P1 completes
                pipeThread.join(); 
                p2.getOutputStream().close(); 

                // 6. Wait for P2 to finish printing the final output
                p2.waitFor();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}