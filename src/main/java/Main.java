import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        // CodeCrafters typical REPL loop setup
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
        // Step 1: Split input into raw tokens by whitespace
        String[] tokens = input.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return;
        }

        List<String> commandArgs = new ArrayList<>();
        String redirectFilePath = null;

        // Step 2: Parse tokens to separate command args from redirection
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].equals(">") || tokens[i].equals("1>")) {
                if (i + 1 < tokens.length) {
                    redirectFilePath = tokens[i + 1];
                    i++; // Skip the filename token
                } else {
                    System.err.println("syntax error near unexpected token 'newline'");
                    return;
                }
            } else {
                commandArgs.add(tokens[i]);
            }
        }

        if (commandArgs.isEmpty()) return;

        // Step 3: Handle execution
        if (commandArgs.get(0).equals("echo")) {
            handleEcho(commandArgs, redirectFilePath);
        } else {
            handleExternalCommand(commandArgs, redirectFilePath);
        }
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
            
            // Redirect stdout (1) to the file, keep stderr (2) attached to terminal
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