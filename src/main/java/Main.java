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

            // Simple split by whitespace to get command and arguments
            String[] parts = input.split("\\s+");
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
                    // Handled in the previous stage (do nothing)
                    break;

                default:
                    // Try to find the external command in the PATH environment variable
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

    /**
     * Helper method to search for a executable binary within the system's PATH variable.
     */
    private static String findInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        // Paths are separated by ":" on Unix/Linux/macOS
        String[] directories = pathEnv.split(":");
        for (String dir : directories) {
            File file = new File(dir, command);
            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * Helper method to execute an external process using ProcessBuilder.
     */
    private static void executeExternalCommand(String[] commandWithArgs) {
        try {
            ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
            
            // Redirect standard inputs, outputs, and errors to inherit from our Java process
            pb.inheritIO();
            
            Process process = pb.start();
            process.waitFor(); // Wait for the external program to finish executing
        } catch (IOException | InterruptedException e) {
            // If execution fails, fallback gracefully
            System.out.println(commandWithArgs[0] + ": command not found");
        }
    }
}