import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    // ---------- Job control state ----------
    private static final Map<Integer, Job> jobs = new TreeMap<>();
    private static final Object jobsLock = new Object();
    private static final AtomicLong orderCounter = new AtomicLong(0);

    private static class Job {
        int number;
        long pid;
        String commandLine;
        long order; // monotonically increasing "start sequence" used for +/- markers
        volatile boolean finished = false;

        Job(int number, long pid, String commandLine, long order) {
            this.number = number;
            this.pid = pid;
            this.commandLine = commandLine;
            this.order = order;
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            // Report any background jobs that finished since the last prompt,
            // BEFORE printing the new prompt (matches bash's notification timing).
            reportFinishedJobs();

            System.out.print("$ ");
            String line = reader.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            List<String> tokens = tokenize(line);
            if (tokens.isEmpty()) continue;

            // Detect trailing "&" -> background execution
            boolean background = false;
            if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("&")) {
                background = true;
                tokens = new ArrayList<>(tokens.subList(0, tokens.size() - 1));
            }

            if (tokens.isEmpty()) continue;

            // Reconstruct the display command line (without trailing "&") for job notifications
            String displayCommand = String.join(" ", tokens);

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
                if (background) {
                    runBackground(stages, displayCommand);
                } else if (stages.size() == 1) {
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
            } else if (c == '&') {
                if (tokenStarted) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    tokenStarted = false;
                }
                tokens.add("&");
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
                    if (Arrays.asList("exit", "echo", "pwd", "cd", "type", "jobs").contains(name)) {
                        System.out.println(name + " is a shell builtin");
                    } else {
                        String path = findExecutable(name);
                        if (path != null) System.out.println(name + " is " + path);
                        else System.out.println(name + ": not found");
                    }
                }
                return;
            case "jobs":
                printJobs();
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

    // ---------- Pipeline of 2+ external commands (foreground) ----------
    private static void runPipeline(List<List<String>> stages) throws Exception {
        List<Process> processes = startPipelineProcesses(stages, true);
        for (Process p : processes) {
            p.waitFor();
        }
    }

    private static List<Process> startPipelineProcesses(List<List<String>> stages, boolean foreground) throws Exception {
        List<ProcessBuilder> builders = new ArrayList<>();
        for (List<String> stage : stages) {
            ProcessBuilder pb = new ProcessBuilder(stage);
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            builders.add(pb);
        }

        if (foreground) {
            builders.get(0).redirectInput(ProcessBuilder.Redirect.INHERIT);
        } else {
            builders.get(0).redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        }
        builders.get(builders.size() - 1).redirectOutput(ProcessBuilder.Redirect.INHERIT);

        if (builders.size() == 1) {
            Process p = builders.get(0).start();
            return Collections.singletonList(p);
        }
        return ProcessBuilder.startPipeline(builders);
    }

    // ---------- Background job execution ----------
    private static void runBackground(List<List<String>> stages, String displayCommand) throws Exception {
        List<Process> processes = startPipelineProcesses(stages, false);
        Process lastProcess = processes.get(processes.size() - 1);
        long pid = lastProcess.pid();

        int jobNumber;
        Job job;
        synchronized (jobsLock) {
            jobNumber = nextAvailableJobNumber();
            long order = orderCounter.incrementAndGet();
            job = new Job(jobNumber, pid, displayCommand, order);
            jobs.put(jobNumber, job);
        }

        System.out.println("[" + jobNumber + "] " + pid);

        Thread reaper = new Thread(() -> {
            try {
                for (Process p : processes) {
                    p.waitFor();
                }
            } catch (InterruptedException ignored) {
            } finally {
                job.finished = true; // detected & reported by reportFinishedJobs() or printJobs()
            }
        });
        reaper.setDaemon(true);
        reaper.start();
    }

    // Finds the smallest positive integer not currently assigned to an active job.
    private static int nextAvailableJobNumber() {
        int n = 1;
        while (jobs.containsKey(n)) {
            n++;
        }
        return n;
    }

    // Prints "[N]+/-  Done                 <command>" for any finished jobs,
    // then removes them so the number can be recycled by a future job.
    // Called before every new prompt is printed.
    private static void reportFinishedJobs() {
        List<Job> done = new ArrayList<>();
        List<Job> snapshotAll;
        synchronized (jobsLock) {
            snapshotAll = new ArrayList<>(jobs.values());
            Iterator<Map.Entry<Integer, Job>> it = jobs.entrySet().iterator();
            while (it.hasNext()) {
                Job j = it.next().getValue();
                if (j.finished) {
                    done.add(j);
                    it.remove();
                }
            }
        }
        for (Job j : done) {
            char sign = computeSign(j, snapshotAll);
            System.out.println(formatStatusLine(j, "Done", sign));
        }
    }

    // Determines the job-control marker: '+' = most recently started active job,
    // '-' = second most recently started, ' ' = older jobs.
    private static char computeSign(Job job, List<Job> allJobs) {
        List<Job> sorted = new ArrayList<>(allJobs);
        sorted.sort((a, b) -> Long.compare(b.order, a.order)); // descending by recency
        if (sorted.isEmpty()) return '+';
        if (sorted.get(0).order == job.order) return '+';
        if (sorted.size() > 1 && sorted.get(1).order == job.order) return '-';
        return ' ';
    }

    private static String formatStatusLine(Job job, String status, char sign) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(job.number).append("]").append(sign).append("  ");
        sb.append(status);
        int pad = 17; // fixed gap observed in expected output, regardless of status word length
        for (int i = 0; i < pad; i++) sb.append(' ');
        sb.append(job.commandLine);
        return sb.toString();
    }

    // The "jobs" builtin: checks EACH job's finished flag at the moment it runs
    // (not just at the next prompt), so a job that completed between the previous
    // prompt and this "jobs" invocation is shown/reaped as "Done" right here,
    // instead of lingering as "Running" until the next loop iteration.
    private static void printJobs() {
        List<Job> snapshot;
        synchronized (jobsLock) {
            snapshot = new ArrayList<>(jobs.values());
        }
        snapshot.sort((a, b) -> Integer.compare(a.number, b.number));

        List<Job> toRemove = new ArrayList<>();
        for (Job j : snapshot) {
            char sign = computeSign(j, snapshot);
            if (j.finished) {
                System.out.println(formatStatusLine(j, "Done", sign));
                toRemove.add(j);
            } else {
                System.out.println(formatStatusLine(j, "Running", sign) + " &");
            }
        }

        if (!toRemove.isEmpty()) {
            synchronized (jobsLock) {
                for (Job j : toRemove) {
                    jobs.remove(j.number);
                }
            }
        }
    }
}