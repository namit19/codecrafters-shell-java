import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        System.out.print("$ ");
        Scanner scanner = new Scanner(System.in);
        
        while (scanner.hasNextLine()) {
            String input = scanner.nextLine();
            try {
                executeCommand(input);
            } catch (Exception e) {
                System.err.println("Execution error: " + e.getMessage());
            }
            System.out.print("$ ");
        }
    }

    public static void executeCommand(String input) throws IOException, InterruptedException {
        // Step 1: Tokenize input, respecting single quotes, double quotes, and backslashes
        List<String> rawTokens = parseArguments(input.trim());
        if (rawTokens.isEmpty()) {
            return;
        }

        List<String> commandArgs = new ArrayList<>();
        String redirectFilePath = null;

        // Step 2: Separate standard command arguments from redirection operators
        for (int i = 0; i < rawTokens.size(); i++) {
            String token = rawTokens.get(i);
            if (token.equals(">") || token.equals("1>")) {
                if (i + 1 < rawTokens.size()) {
                    redirectFilePath = rawTokens.get(i + 1);
                    i++; // Skip the filename token
                } else {
                    System.err.println("syntax error near unexpected token 'newline'");
                    return;
                }
            } else {
                commandArgs.add(token);
            }
        }

        if (commandArgs.isEmpty()) return;

        // Step 3: Route command to built-ins or external processes
        String command = commandArgs.get(0);
        if (command.equals("echo")) {
            handleEcho(commandArgs, redirectFilePath);
        } else if (command.equals("cd")) {
            handleCd(commandArgs);
        } else {
            handleExternalCommand(commandArgs, redirectFilePath);
        }
    }

    /**
     * Advanced Shell Tokenizer handling Single Quotes, Double Quotes, and Backslashes.
     */
    private static List<String> parseArguments(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean hasContent = false; // Ensures empty structures like "" or '' register tokens

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                    hasContent = true; 
                } else {
                    currentToken.append(c);
                }
            } else if (inDoubleQuotes) {
                if (c == '"') {
                    inDoubleQuotes = false;
                    hasContent = true;
                } else if (c == '\\' && i + 1 < input.length()) {
                    // Inside double quotes, backslash only escapes specific characters
                    char next = input.charAt(i + 1);
                    if (next == '\\' || next == '"' || next == '$' || next == '\n') {
                        currentToken.append(next);
                        i++;
                    } else {
                        currentToken.append(c); 
                    }
                } else {
                    currentToken.append(c);
                }
            } else {
                // Outside of any active quotes
                if (c == '\'') {
                    inSingleQuotes = true;
                } else if (c == '"') {
                    inDoubleQuotes = true;
                } else if (c == '\\' && i + 1 < input.length()) {
                    // Backslash outside quotes escapes the immediate next character unconditionally
                    currentToken.append(input.charAt(i + 1));
                    hasContent = true;
                    i++;
                } else if (Character.isWhitespace(c)) {
                    if (currentToken.length() > 0 || hasContent) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                        hasContent = false;
                    }
                } else {
                    currentToken.append(c);
                    hasContent = true;
                }
            }
        }

        // Catch trailing leftover tokens
        if (currentToken.length() > 0 || hasContent) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

    /**
     * Handles 'echo' builtin string building and routing.
     */
    private static void handleEcho(List<String> args, String redirectPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.size(); i++) {
            sb.append(args.get(i));
            if (i < args.size() - 1) sb.append(" ");
        }
        sb.append("\n");

        if (redirectPath != null) {
            File file = new File(redirectPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            java.nio.file.Files.writeString(file.toPath(), sb.toString());
        } else {
            System.out.print(sb.toString());
        }
    }

    /**
     * Handles 'cd' builtin navigation using state properties.
     */
    private static void handleCd(List<String> args) {
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

        // Resolve relative paths against the shell's active working directory
        if (!directory.isAbsolute()) {
            directory = new File(System.getProperty("user.dir"), targetPath);
        }

        if (directory.exists() && directory.isDirectory()) {
            System.setProperty("user.dir", directory.getAbsolutePath());
        } else {
            System.err.println("cd: " + args.get(1) + ": No such file or directory");
        }
    }

    /**
     * Executes external binaries with active directory constraints and output redirections.
     */
    private static void handleExternalCommand(List<String> args, String redirectPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);
        
        // Dynamic directory syncing so subsequent commands track 'cd' changes
        pb.directory(new File(System.getProperty("user.dir")));

        if (redirectPath != null) {
            File outputFile = new File(redirectPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            pb.redirectOutput(ProcessBuilder.Redirect.to(outputFile));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT); // Stderr stays on terminal
        } else {
            pb.inheritIO();
        }

        try {
            Process process = pb.start();
            process.waitFor();
        } catch (IOException e) {
            System.err.println(args.get(0) + ": command not found");
        }
    }
}