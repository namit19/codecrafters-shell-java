import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) break;
            
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            // Parse inputs into tokens, preserving spaces where necessary if quotes exist
            String[] parts = parseArguments(input);
            if (parts.length == 0) continue;
            
            String command = parts[0];

            switch (command) {
                case "exit":
                    System.exit(0);
                    break;

                case "type":
                    if (parts.length > 1) {
                        String target = parts[1];
                        if (target.equals("jobs") || target.equals("exit") || target.equals("type")) {
                            System.out.println(target + " is a shell builtin");
                        } else {
                            String pathToFile = findInPath(target);
                            if (pathToFile != null) {
                                System.out.println(target + " is " + pathToFile);
                            } else {
                                System.out.println(target + ": not found");
                            }
                        }
                    }
                    break;

                case "jobs":
                    // Intentional blank implementation for the jobs builtin stage
                    break;

                default:
                    String executablePath = findInPath(command);
                    if (executablePath != null) {
                        executeExternalCommand(parts);
                    } else {
                        System.out.println(command + ": command not found");
                    }
                    break;
            }
        }
        scanner.close();
    }

    private static String findInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        String[] directories = pathEnv.split(":");
        for (String dir : directories) {
            File file = new File(dir, command);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static void executeExternalCommand(String[] rawArgs) {
        List<String> commandArgs = new ArrayList<>();
        String redirectFile = null;
        boolean appendMode = false;
        boolean redirectStdout = false;
        boolean redirectStderr = false;

        // Parse redirection tokens out of the arguments array
        for (int i = 0; i < rawArgs.length; i++) {
            String arg = rawArgs[i];
            if (arg.equals(">") || arg.equals("1>")) {
                redirectStdout = true;
                appendMode = false;
                if (i + 1 < rawArgs.length) redirectFile = rawArgs[++i];
            } else if (arg.equals(">>") || arg.equals("1>>")) {
                redirectStdout = true;
                appendMode = true;
                if (i + 1 < rawArgs.length) redirectFile = rawArgs[++i];
            } else if (arg.equals("2>")) {
                redirectStderr = true;
                appendMode = false;
                if (i + 1 < rawArgs.length) redirectFile = rawArgs[++i];
            } else if (arg.equals("2>>")) {
                redirectStderr = true;
                appendMode = true;
                if (i + 1 < rawArgs.length) redirectFile = rawArgs[++i];
            } else {
                commandArgs.add(arg);
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(commandArgs);

            if (redirectFile != null) {
                File targetFile = new File(redirectFile);
                // Ensure parent directories exist for the redirection target
                File parentDir = targetFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                if (redirectStdout) {
                    pb.redirectOutput(appendMode ? ProcessBuilder.Redirect.appendTo(targetFile) : ProcessBuilder.Redirect.to(targetFile));
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                } else if (redirectStderr) {
                    pb.redirectError(appendMode ? ProcessBuilder.Redirect.appendTo(targetFile) : ProcessBuilder.Redirect.to(targetFile));
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                }
            } else {
                pb.inheritIO();
            }

            Process process = pb.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println(rawArgs[0] + ": command not found");
        }
    }

    /**
     * Splits arguments accurately while respecting spaces inside quotes if present.
     */
    private static String[] parseArguments(String input) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (sb.length() > 0) {
                    list.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            list.add(sb.toString());
        }
        return list.toArray(new String[0]);
    }
}