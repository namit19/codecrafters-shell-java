import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ShellRedirect {

    public static void main(String[] args) {
        // Simulating an input string from your shell prompt
        // Example 1: Standard command
        // String input = "cat /tmp/baz/blueberry nonexistent 1> /tmp/foo/quz.md";
        // Example 2: Simple echo
        String input = "echo Hello James > /tmp/foo/foo.md";

        try {
            executeCommand(input);
        } catch (Exception e) {
            System.err.println("Execution error: " + e.getMessage());
        }
    }

    public static void processCommand(String input) throws IOException, InterruptedException {
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
                    // Skip the filename token since we've captured it
                    i++; 
                } else {
                    System.err.println("syntax error near unexpected token 'newline'");
                    return;
                }
            } else {
                commandArgs.add(tokens[i]);
            }
        }

        // Step 3: Handle execution
        if (commandArgs.isEmpty()) return;

        // Handle shell built-ins manually (like echo)
        if (commandArgs.get(0).equals("echo")) {
            handleEcho(commandArgs, redirectFilePath);
        } else {
            // Handle external system binaries (cat, ls, pwd, etc.)
            handleExternalCommand(commandArgs, redirectFilePath);
        }
    }

    private static void handleEcho(List<String> args, String redirectPath) throws IOException {
        // Reconstruct the echo string (skipping the word 'echo' itself)
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.size(); i++) {
            sb.append(args.get(i));
            if (i < args.size() - 1) sb.append(" ");
        }
        sb.append("\n");

        if (redirectPath != null) {
            // Write directly to the file (overwriting existing content)
            File file = new File(redirectPath);
            // Ensure parent directories exist if testing environment expects it
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
            
            // Redirects standard output (1) to the file
            pb.redirectOutput(ProcessBuilder.Redirect.to(outputFile));
            // Keep standard error (2) bound to the console
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        } else {
            // No redirection, pipe everything to terminal
            pb.inheritIO();
        }

        try {
            Process process = pb.start();
            process.waitFor(); // Wait for the command to finish executing
        } catch (IOException e) {
            // Handle case where binary doesn't exist on system PATH
            System.err.println(args.get(0) + ": command not found");
        }
    }
}