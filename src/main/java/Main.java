import java.util.*;
import java.io.*;

public class Main {
    private static final Set<String> BUILTINS = new HashSet<>(Arrays.asList("exit", "echo", "type", "cd", "pwd"));

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        if (System.getProperty("user.dir") == null) {
            System.setProperty("user.dir", new File(".").getAbsolutePath());
        }

        while (true) {
            System.out.print("$ ");
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
                for (int i = 1; i < parsedArgs.size(); i++) {
                    System.out.print(parsedArgs.get(i));
                    if (i < parsedArgs.size() - 1) {
                        System.out.print(" ");
                    }
                }
                System.out.println();
            } else if (command.equals("type")) {
                handleTypeCommand(parsedArgs);
            } else if (command.equals("cd")) {
                handleCdCommand(parsedArgs);
            } else if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else {
                executeExternalCommand(parsedArgs);
            }
        }
        scanner.close();
    }

    private static List<String> parseArguments(String commandLine) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean hasContent = false; 

        for (int i = 0; i < commandLine.length(); i++) {
            char ch = commandLine.charAt(i);

            // Handle backslash outside of any quotes
            if (ch == '\\' && !inSingleQuotes && !inDoubleQuotes) {
                if (i + 1 < commandLine.length()) {
                    i++; 
                    currentArg.append(commandLine.charAt(i)); 
                    hasContent = true;
                }
            } else if (ch == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                hasContent = true; 
            } else if (ch == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                hasContent = true;
            } else if (inSingleQuotes || inDoubleQuotes) {
                // When inSingleQuotes is true, backslashes fall straight into here
                // and are safely appended as literal characters.
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

    private static void handleTypeCommand(List<String> args) {
        if (args.size() < 2) {
            return;
        }
        String targetCommand = args.get(1);

        if (BUILTINS.contains(targetCommand)) {
            System.out.println(targetCommand + " is a shell builtin");
            return;
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String delimiter = System.getProperty("path.separator");
            for (String path : pathEnv.split(delimiter)) {
                File exe = new File(path, targetCommand);
                if (exe.isFile() && exe.canExecute()) {
                    System.out.println(targetCommand + " is " + exe.getAbsolutePath());
                    return;
                }
            }
        }

        System.out.println(targetCommand + ": not found");
    }

    private static void handleCdCommand(List<String> args) {
        String targetPath;

        if (args.size() < 2 || args.get(1).equals("~")) {
            targetPath = System.getenv("HOME");
            if (targetPath == null) {
                targetPath = System.getProperty("user.home");
            }
        } else {
            targetPath = args.get(1);
        }

        File directory = new File(targetPath);
        if (!directory.isAbsolute()) {
            directory = new File(System.getProperty("user.dir"), targetPath);
        }

        if (directory.exists() && directory.isDirectory()) {
            try {
                System.setProperty("user.dir", directory.getCanonicalPath());
            } catch (IOException e) {
                System.setProperty("user.dir", directory.getAbsolutePath());
            }
        } else {
            System.out.println("cd: " + args.get(1) + ": No such file or directory");
        }
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
            pb.directory(new File(System.getProperty("user.dir"))); 
            pb.inheritIO(); 
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            System.out.println(command + ": error running command");
        }
    }
}