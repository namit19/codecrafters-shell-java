import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

public class Main {
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
                case "exit":
                    int code = commandArgs.isEmpty() ? 0 : Integer.parseInt(commandArgs.get(0));
                    System.exit(code);
                    break;
                default:
                    System.out.println(command + ": command not found");
            }
        }
    }

    // Simple whitespace tokenizer (splits on runs of spaces)
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