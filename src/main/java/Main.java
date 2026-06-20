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
                // Peek at the next character if it exists
                if (i + 1 < commandLine.length()) {
                    i++; // Skip the backslash character index
                    currentArg.append(commandLine.charAt(i)); // Add next char literally
                    hasContent = true;
                }
            } else if (ch == '\'' && !inDoubleQuotes) {
                // Toggle single quotes ONLY if we aren't inside double quotes
                inSingleQuotes = !inSingleQuotes;
                hasContent = true; 
            } else if (ch == '"' && !inSingleQuotes) {
                // Toggle double quotes ONLY if we aren't inside single quotes
                inDoubleQuotes = !inDoubleQuotes;
                hasContent = true;
            } else if (inSingleQuotes || inDoubleQuotes) {
                // Inside any active quote block, treat everything literally
                currentArg.append(ch);
            } else {
                // Outside all quotes and unescaped, handle spacing and delimiters
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
            String delimiter