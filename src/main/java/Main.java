import java.util.*;
import java.io.*;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ "); // Fixed here
            System.out.flush();

            if (!scanner.hasNextLine()) {
                break;
            }

            String commandLine = scanner.nextLine();
            if (commandLine.trim().isEmpty()) {
                continue;
            }

            List<String> parsedArgs = parseArguments(commandLine);
            if (parsedArgs.isEmpty()) {
                continue;
            }

            String command = parsedArgs.get(0);

            // Handle built-in commands
            if (command.equals("exit")) {
                System.exit(0);
            } else if (command.equals("echo")) {
                // Print arguments separated by a single space
                for (int i = 1; i < parsedArgs.size(); i++) {
                    System.out.print(parsedArgs.get(i)); // Fixed here
                    if (i < parsedArgs.size() - 1) {
                        System.out.print(" "); // Fixed here
                    }
                }
                System.out.println();
            } else {
                // Handle external executables (like cat)
                executeExternalCommand(parsedArgs);
            }
        }
        scanner.close();
    }

    private static List<String> parseArguments(String commandLine) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean hasContent = false; 

        for (int i = 0; i < commandLine.length(); i++) {
            char ch = commandLine.charAt(i);

            if (ch == '\'') {
                inSingleQuotes = !inSingleQuotes;
                hasContent = true; 
            } else if (inSingleQuotes) {
                currentArg.append(ch);
            } else {
                if (Character.isWhitespace(ch)) {
                    if (currentArg.length() > 0 || hasContent) {
                        args.add(currentArg.toString());
                        currentArg.setLength(0); 
                        hasContent = false;
                    }
                } else {
                    currentArg.append(ch);
                    hasContent = true;
                }
            }
        }

        if (currentArg.length() > 0 || hasContent) {
            args.add(currentArg.toString());
        }

        return args;
    }

    private static void executeExternalCommand(List<String> args) {
        String command = args.get(0);
        String pathEnv = System.getenv("PATH");
        boolean found = false;

        if (pathEnv != null) {
            String delimiter = System.getProperty("path.separator");
            for (String path : pathEnv.split(delimiter)) {
                File exe = new File(path, command);
                if (exe.isFile() && exe.canExecute()) {
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            System.out.println(command + ": command not found");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.inheritIO(); 
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            System.out.println(command + ": error running command");
        }
    }
}