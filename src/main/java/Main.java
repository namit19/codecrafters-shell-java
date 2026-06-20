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
        List<String> rawTokens = parseArguments(input.trim());
        if (rawTokens.isEmpty()) {
            return;
        }

        List<String> commandArgs = new ArrayList<>();
        String redirectFilePath = null;

        // Separate command args from redirection operators
        for (int i = 0; i < rawTokens.size(); i++) {
            String token = rawTokens.get(i);
            if (token.equals(">") || token.equals("1>")) {
                if (i + 1 < rawTokens.size()) {
                    redirectFilePath = rawTokens.get(i + 1);
                    i++; // Skip filename
                } else {
                    System.err.println("syntax error near unexpected token 'newline'");
                    return;
                }
            } else {
                commandArgs.add(token);
            }
        }

        if (commandArgs.isEmpty()) return;

        if (commandArgs.get(0).equals("echo")) {
            handleEcho(commandArgs, redirectFilePath);
        } else {
            handleExternalCommand(commandArgs, redirectFilePath);
        }
    }

    /**
     * Correct Shell Tokenizer handling Single Quotes, Double Quotes, and Backslashes.
     */
    private static List<String> parseArguments(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean hasContent = false; 

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
                if (c == '\'') {
                    inSingleQuotes = true;
                } else if (c == '"') {
                    inDoubleQuotes = true;
                } else if (c == '\\' && i + 1 < input.length()) {
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

        if (currentToken.length() > 0 || hasContent) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }

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

    private static void handleExternalCommand(List<String> args, String redirectPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(args);

        if (redirectPath != null) {
            File outputFile = new File(redirectPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            pb.redirectOutput(ProcessBuilder.Redirect.to(outputFile));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
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