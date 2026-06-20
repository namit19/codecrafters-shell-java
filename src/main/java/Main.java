import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("$ ");
            String line = reader.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            List<String> tokens = tokenize(line); // your existing quote-aware tokenizer

            // Split tokens into pipeline stages on the "|" token
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

            if (stages.size() == 1) {
                runSingleCommand(stages.get(0)); // existing logic (builtins, etc.)
            } else {
                runPipeline(stages);
            }
        }
    }

    private static void runPipeline(List<List<String>> stages) throws Exception {
        List<ProcessBuilder> builders = new ArrayList<>();
        for (List<String> stage : stages) {
            ProcessBuilder pb = new ProcessBuilder(stage);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            builders.add(pb);
        }

        // First process inherits stdin, last inherits stdout.
        builders.get(0).redirectInput(ProcessBuilder.Redirect.INHERIT);
        builders.get(builders.size() - 1).redirectOutput(ProcessBuilder.Redirect.INHERIT);

        // This wires stdout[i] -> stdin[i+1] using real OS pipes.
        List<Process> processes = ProcessBuilder.startPipeline(builders);

        // Wait for all processes (important for tail -f | head to behave correctly:
        // head exits after N lines, which should close the pipe; tail will get SIGPIPE/EPIPE
        // on next write attempt and the kernel handles cleanup).
        for (Process p : processes) {
            p.waitFor();
        }
    }
}