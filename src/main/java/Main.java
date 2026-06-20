import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("$ ");
            String line = reader.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            List<String> tokens = tokenize(line);
            if (tokens.isEmpty()) continue;

            // Split tokens into pipeline stages on "|"
            List<List<String>> stages = new ArrayList<>();
            List<String> current = new ArrayList<>();
            for (String tok : tokens) {
                if (tok.equals("|")) {
                    stages.add(current);
                    current = new ArrayList<>();
                } else {
                    current.add(tok);
                }
            }
            stages.add(current);

            try {
                if (stages.size() == 1) {
                    runSingleCommand(stages.get(0));
                } else {
                    runPipeline(stages);
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    // ---------- Tokenizer (handles single quotes, double quotes, backslash escapes) ----------
    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false;
        boolean tokenStarted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inSingle) {
                if (c == '\'') inSingle = false;
                else cur.append(c);
                continue;
            }
            if (inDouble) {
                if (c == '"') {
                    inDouble = false;
                } else if (c == '\\' && i + 1 < line.length() &&
                        (line.charAt(i + 1) == '"' || line.charAt(i + 1) == '\\' || line.charAt(i + 1) == '$')) {
                    cur.append(line.charAt(i + 1));
                    i++;
                } else {
                    cur.append(c);
                }
                continue;
            }

            if (c == '\'') {
                inSingle = true;
                tokenStarted = true;
            } else if (c == '"') {
                inDouble = true;
                tokenStarted = true;
            } else if (c == '\\' && i + 1 < line.length()) {
                cur.append(line.charAt(i + 1));
                i++;
                tokenStarted = true;
            } else if (c == '|') {
                if (tokenStarted) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    tokenStarted = false;
                }
                tokens.add("|");
            } else if (Character.isWhitespace(c)) {
                if (tokenStarted) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    tokenStarted = false;
                }
            } else {
                cur.append(c);
                tokenStarted = true;
            }
        }
        if (tokenStarted) tokens.add(cur.toString());
        return tokens;
    }

    // ---------- Single command (builtins + external) ----------
    private static void runSingleCommand(List<String> tokens) throws Exception {
        if (tokens.isEmpty()) return;
        String cmd = tokens.get(0);
        List<String> cmdArgs = tokens.subList(1, tokens.size());

        switch (cmd) {
            case "exit":
                System.exit(cmdArgs.isEmpty() ? 0 : Integer.parseInt(cmdArgs.get(0)));
                return;
            case "echo":
                System.out.println(String.join(" ", cmdArgs));
                return;
            case "pwd":
                System.out.println(System.getProperty("user.dir"));
                return;
            case "cd":
                String target = cmdArgs.isEmpty() ? System.getenv("HOME") : cmdArgs.get(0);
                if (target.equals("~")) target = System.getenv("HOME");
                File dir = new File(target);
                if (!dir.isAbsolute()) dir = new File(System.getProperty("user.dir"), target);
                if (dir.exists() && dir.isDirectory()) {
                    System.setProperty("user.dir", dir.getCanonicalPath());
                } else {
                    System.out.println("cd: " + target + ": No such file or directory");
                }
                return;
            case "type":
                if (!cmdArgs.isEmpty()) {
                    String name = cmdArgs.get(0);
                    if (Arrays.asList("exit", "echo", "pwd", "cd", "type").contains(name)) {
                        System.out.println(name + " is a shell builtin");
                    } else {
                        String path = findExecutable(name);
                        if (path != null) System.out.println(name + " is " + path);
                        else System.out.println(name + ": not found");
                    }
                }
                return;
            default:
                runExternal(tokens);
        }
    }

    private static void runExternal(List<String> tokens) throws Exception {
        String exe = findExecutable(tokens.get(0));
        if (exe == null) {
            System.out.println(tokens.get(0) + ": command not found");
            return;
        }
        ProcessBuilder pb = new ProcessBuilder(tokens);
        pb.directory(new File(System.getProperty("user.dir")));
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        p.waitFor();
    }

    private static String findExecutable(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File f = new File(dir, name);
            if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }

    // ---------- Pipeline of 2+ external commands ----------
    private static void runPipeline(List<List<String>> stages) throws Exception {
        List<ProcessBuilder> builders = new ArrayList<>();
        for (List<String> stage : stages) {
            ProcessBuilder pb = new ProcessBuilder(stage);
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            builders.add(pb);
        }

        builders.get(0).redirectInput(ProcessBuilder.Redirect.INHERIT);
        builders.get(builders.size() - 1).redirectOutput(ProcessBuilder.Redirect.INHERIT);

        List<Process> processes = ProcessBuilder.startPipeline(builders);

        for (Process p : processes) {
            p.waitFor();
        }
    }
}