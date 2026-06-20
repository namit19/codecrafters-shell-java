import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static String currentDir = System.getProperty("user.dir");

    // Background job tracking
    private static final List<Job> jobs = new ArrayList<>();

    private static class Job {
        int number;
        long pid;
        String command;
        Process process;

        Job(int number, long pid, String command, Process process) {
            this.number = number;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            // Automatic reaping before each prompt: Only prints and removes "Done" jobs
            reapDeadJobsBeforePrompt();

            System.out.print("$ ");
            if (!scanner.hasNextLine()) break;

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            List<String> tokens = parseArguments(input);
            if (tokens.isEmpty()) continue;

            boolean background = false;
            if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("&")) {
                background = true;
                tokens.remove(tokens.size() - 1);
                if (tokens.isEmpty()) continue;
            }

            String command = tokens.get(0);
            String[] parts = tokens.toArray(new String[0]);

            if (background) {
                String executablePath = findInPath(command);
                if (executablePath != null) {
                    runInBackground(parts, input);
                } else {
                    System.out.println(command + ": command not found");
                }
                continue;
            }

            switch (command) {
                case "exit":
                    System.exit(0);
                    break;

                case "type":
                    if (parts.length > 1) {
                        String target = parts[1];
                        if (target.equals("jobs") || target.equals("exit") || target.equals("type")
                                || target.equals("cd") || target.equals("pwd") || target.equals("echo")) {
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
                    List<Job> reaped = new ArrayList<>();

                    // Loop through all tracked jobs sequentially by job number
                    for (int i = 0; i < jobs.size(); i++) {
                        Job job = jobs.get(i);
                        char marker = ' ';

                        // Markers are evaluated relative to the absolute current printing snapshot
                        if (jobs.size() == 1 || i == jobs.size() - 1) {
                            marker = '+';
                        } else if (i == jobs.size() - 2) {
                            marker = '-';
                        }

                        if (job.process.isAlive()) {
                            String statusField = String.format("%-24s", "Running");
                            System.out.println("[" + job.number + "]" + marker + "  " + statusField + job.command);
                        } else {
                            String statusField = String.format("%-24s", "Done");
                            System.out.println("[" + job.number + "]" + marker + "  " + statusField + cleanCommand(job.command));
                            reaped.add(job);
                        }
                    }

                    // Flush dead elements out of global list entirely after printing
                    jobs.removeAll(reaped);
                    break;

                case "cd":
                    handleCd(parts);
                    break;

                case "pwd":
                    System.out.println(currentDir);
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

    private static void reapDeadJobsBeforePrompt() {
        List<Job> reaped = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            if (!job.process.isAlive()) {
                char marker = ' ';
                if (jobs.size() == 1 || i == jobs.size() - 1) {
                    marker = '+';
                } else if (i == jobs.size() - 2) {
                    marker = '-';
                }

                String statusField = String.format("%-24s", "Done");
                System.out.println("[" + job.number + "]" + marker + "  " + statusField + cleanCommand(job.command));
                reaped.add(job);
            }
        }
        jobs.removeAll(reaped);
    }

    private static String cleanCommand(String cmd) {
        if (cmd.endsWith(" &")) {
            return cmd.substring(0, cmd.length() - 2);
        } else if (cmd.endsWith("&")) {
            return cmd.substring(0, cmd.length() - 1);
        }
        return cmd;
    }

    private static void runInBackground(String[] rawArgs, String originalCommandLine) {
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
            pb.directory(new File(currentDir));

            if (redirectFile != null) {
                File targetFile = resolvePath(redirectFile);
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

            // Dynamic Job Number Recycling Logic
            int targetJobNumber = 1;
            if (!jobs.isEmpty()) {
                int currentMax = 0;
                for (Job job : jobs) {
                    if (job.number > currentMax) {
                        currentMax = job.number;
                    }
                }
                targetJobNumber = currentMax + 1;
            }

            long pid = process.pid();
            jobs.add(new Job(targetJobNumber, pid, originalCommandLine, process));

            System.out.println("[" + targetJobNumber + "] " + pid);
        } catch (IOException e) {
            System.out.println(rawArgs[0] + ": command not found");
        }
    }

    private static void handleCd(String[] parts) {
        String target;
        if (parts.length < 2 || parts[1].isEmpty()) {
            target = "~";
        } else {
            target = parts[1];
        }

        if (target.equals("~")) {
            String home = System.getenv("HOME");
            if (home == null) {
                System.out.println("cd: HOME not set");
                return;
            }
            target = home;
        } else if (target.startsWith("~/")) {
            String home = System.getenv("HOME");
            if (home == null) {
                System.out.println("cd: HOME not set");
                return;
            }
            target = home + target.substring(1);
        }

        File dir = resolvePath(target);
        if (dir.isDirectory()) {
            try {
                currentDir = dir.getCanonicalPath();
            } catch (IOException e) {
                currentDir = dir.getAbsolutePath();
            }
        } else {
            System.out.println("cd: " + target + ": No such file or directory");
        }
    }

    private static File resolvePath(String path) {
        File f = new File(path);
        if (f.isAbsolute()) {
            return f;
        }
        return new File(currentDir, path);
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
            pb.directory(new File(currentDir));

            if (redirectFile != null) {
                File targetFile = resolvePath(redirectFile);
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
                hasContent = true;
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