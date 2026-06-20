import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static String currentDir = System.getProperty("user.dir");

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("$ ");
            String input = reader.readLine();

            if (input == null) {
                break; // EOF (Ctrl+D)
            }

            input = input.trim();
            if (input.isEmpty()) {
                continue;
            }

            List<String> tokens = parseInput(input);
            String command = tokens.get(0);
            List<String> commandArgs = tokens.subList(1, tokens.size());

            switch (command) {
                case "echo":
                    System.out.println(String.join(" ", commandArgs));
                    break;
                case "pwd":
                    System.out.println(currentDir);
                    break;
                case "cd":
                    handleCd(commandArgs);
                    break;
                case "exit":
                    int code = commandArgs.isEmpty() ? 0 : Integer.parseInt(commandArgs.get(0));
                    System.exit(code);
                    break;
                default:
                    System.out.println(command + ": command not found");
            }
        }
    }

    private static void handleCd(List<String> commandArgs) {
        if (commandArgs.isEmpty()) {
            return; // could default to HOME, but not required here
        }

        String target = commandArgs.get(0);

        // Handle ~ (home directory)
        if (target.equals("~")) {
            target = System.getenv("HOME");
        } else if (target.startsWith("~/")) {
            target = System.getenv("HOME") + target.substring(1);
        }

        File targetFile;
        if (target.startsWith("/")) {
            // Absolute path
            targetFile = new File(target);
        } else {
            
            targetFile = new File(currentDir, target);
        }

        String normalizedPath;
        try {
            normalizedPath = targetFile.getCanonicalPath();
        } catch (IOException e) {
            System.out.println("cd: " + commandArgs.get(0) + ": No such file or directory");
            return;
        }

        File resolved = new File(normalizedPath);
        if (resolved.exists() && resolved.isDirectory()) {
            currentDir = normalizedPath;
        } else {
            System.out.println("cd: " + commandArgs.get(0) + ": No such file or directory");
        }
    }

    private static List<String> parseInput(String input) {
        List<String> tokens = new ArrayList<>();
        for (String part : input.split("\\s+")) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        return tokens;
    }
}