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

            // Parse the entire line into tokens at once, respecting quotes
            List<String> tokens = parseArguments(input);
            if (tokens.isEmpty()) continue;
            
            String command = tokens.get(0);
            // Convert to array for compatibility with the rest of your architecture
            String[] parts = tokens.toArray(new String[0]);

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
     * A unified state-machine parsing loop that handles spaces, 
     * single quotes, double quotes, and backslashes correctly.
     */
    private static List<String> parseArguments(String input) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;
        boolean hasContent = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                sb.append(c);
                hasContent = true;
                escaped = false;
            } else if (c == '\\' && !inSingleQuotes) {
                // In double quotes, backslash only escapes specific characters
                if (inDoubleQuotes) {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '$' || next == '`' || next == '"' || next == '\\' || next == '\n') {
                            escaped = true;
                        } else {
                            sb.append(c);
                            hasContent = true;
                        }
                    } else {
                        sb.append(c);
                        hasContent = true;
                    }
                } else {
                    escaped = true;
                }
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                hasContent = true; // Marks that a token exists even if empty (e.g. '')
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                hasContent = true;
            } else if (c == ' ' && !inSingleQuotes && !inDoubleQuotes) {
                if (sb.length() > 0 || hasContent) {
                    list.add(sb.toString());
                    sb.setLength(0);
                    hasContent = false;
                }
            } else {
                sb.append(c);
                hasContent = true;
            }
        }

        if (sb.length() > 0 || hasContent) {
            list.add(sb.toString());
        }

        return list;
    }
}