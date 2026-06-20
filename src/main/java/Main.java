import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    private static String currentDir = System.getProperty("user.dir");
    private static final List<String> BUILTINS = Arrays.asList("echo", "exit", "type", "pwd", "cd");
    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final PrintStream ORIGINAL_ERR = System.err;

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("$ ");
            System.out.flush();
            String input = reader.readLine();

            if (input == null) {
                break;
            }

            if (input.trim().isEmpty()) {
                continue;
            }

            List<String> tokens = parseInput(input);
            if (tokens.isEmpty()) {
                continue;
            }

            // Extract redirection targets (if any) and strip them from the token list
            String stdoutFile = null;
            String stderrFile = null;
            List<String> cleanedTokens = new ArrayList<>();
            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);
                if (t.equals(">") || t.equals("1>")) {
                    if (i + 1 < tokens.size()) {
                        stdoutFile = tokens.get(i + 1);
                        i++;
                    }
                } else if (t.equals("2>")) {
                    if (i + 1 < tokens.size()) {
                        stderrFile = tokens.get(i + 1);
                        i++;
                    }
                } else {
                    cleanedTokens.add(t);
                }
            }

            if (cleanedTokens.isEmpty()) {
                continue;
            }

            String command = cleanedTokens.get(0);
            List<String> commandArgs = cleanedTokens.subList(1, cleanedTokens.size());

            PrintStream stdoutStream = null;
            PrintStream stderrStream = null;

            try {
                if (stdoutFile != null) {
                    File f = resolveFile(stdoutFile);
                    stdoutStream = new PrintStream(new FileOutputStream(f));
                    System.setOut(stdoutStream);
                }
                if (stderrFile != null) {
                    File f = resolveFile(stderrFile);
                    stderrStream = new PrintStream(new FileOutputStream(f));
                    System.setErr(stderrStream);
                }
            } catch (IOException e) {
                System.out.println("cannot create redirect file");
                continue;
            }

            try {
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
                    case "type":
                        handleType(commandArgs);
                        break;
                    case "exit":
                        int code = commandArgs.isEmpty() ? 0 : Integer.parseInt(commandArgs.get(0));
                        System.exit(code);
                        break;
                    default:
                        runExternalCommand(command, cleanedTokens, stdoutFile, stderrFile);
                }
            } finally {
                if (stdoutStream != null) {
                    stdoutStream.flush();
                    stdoutStream.close();
                    System.setOut(ORIGINAL_OUT);
                }
                if (stderrStream != null) {
                    stderrStream.flush();
                    stderrStream.close();
                    System.setErr(ORIGINAL_ERR);
                }
            }

            System.out.flush();
        }
    }

    private static File resolveFile(String name) {
        File f = new File(name);
        if (!f.isAbsolute()) {
            f = new File(currentDir, name);
        }
        return f;
    }

    private static void runExternalCommand(String command, List<String> tokens, String stdoutFile, String stderrFile) {
        File executable = findExecutable(command);

        if (executable == null) {
            System.err.println(command + ": command not found");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(tokens);
            pb.directory(new File(currentDir));

            if (stdoutFile != null) {
                pb.redirectOutput(resolveFile(stdoutFile));
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (stderrFile != null) {
                pb.redirectError(resolveFile(stderrFile));
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

            Process process = pb.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println(command + ": command not found");
        }
    }

    private static File findExecutable(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File candidate = new File(dir, name);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate;
            }
        }
        return null;
    }

    private static void handleType(List<String> commandArgs) {
        if (commandArgs.isEmpty()) {
            return;
        }
        String name = commandArgs.get(0);

        if (BUILTINS.contains(name)) {
            System.out.println(name + " is a shell builtin");
            return;
        }

        File found = findExecutable(name);
        if (found != null) {
            System.out.println(name + " is " + found.getPath());
            return;
        }

        System.out.println(name + ": not found");
    }

    private static void handleCd(List<String> commandArgs) {
        if (commandArgs.isEmpty()) {
            return;
        }

        String target = commandArgs.get(0);

        if (target.equals("~")) {
            String home = System.getenv("HOME");
            if (home == null) {
                System.err.println("cd: HOME not set");
                return;
            }
            target = home;
        } else if (target.startsWith("~/")) {
            String home = System.getenv("HOME");
            if (home == null) {
                System.err.println("cd: HOME not set");
                return;
            }
            target = home + target.substring(1);
        }

        File targetFile;
        if (target.startsWith("/")) {
            targetFile = new File(target);
        } else {
            targetFile = new File(currentDir, target);
        }

        String normalizedPath;
        try {
            normalizedPath = targetFile.getCanonicalPath();
        } catch (IOException e) {
            System.err.println("cd: " + commandArgs.get(0) + ": No such file or directory");
            return;
        }

        File resolved = new File(normalizedPath);
        if (resolved.exists() && resolved.isDirectory()) {
            currentDir = normalizedPath;
        } else {
            System.err.println("cd: " + commandArgs.get(0) + ": No such file or directory");
        }
    }

    // Quote-aware tokenizer: handles single quotes, double quotes, and backslash escapes
    private static List<String> parseInput(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean hasToken = false;

        int i = 0;
        int len = input.length();

        while (i < len) {
            char c = input.charAt(i);

            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                } else {
                    current.append(c);
                }
                i++;
                continue;
            }

            if (inDoubleQuotes) {
                if (c == '"') {
                    inDoubleQuotes = false;
                } else if (c == '\\' && i + 1 < len &&
                        (input.charAt(i + 1) == '"' || input.charAt(i + 1) == '\\' ||
                         input.charAt(i + 1) == '$' || input.charAt(i + 1) == '`')) {
                    current.append(input.charAt(i + 1));
                    i++;
                } else {
                    current.append(c);
                }
                i++;
                continue;
            }

            if (c == '\'') {
                inSingleQuotes = true;
                hasToken = true;
                i++;
                continue;
            }

            if (c == '"') {
                inDoubleQuotes = true;
                hasToken = true;
                i++;
                continue;
            }

            if (c == '\\' && i + 1 < len) {
                current.append(input.charAt(i + 1));
                hasToken = true;
                i += 2;
                continue;
            }

            if (Character.isWhitespace(c)) {
                if (hasToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
                i++;
                continue;
            }

            current.append(c);
            hasToken = true;
            i++;
        }

        if (hasToken) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}