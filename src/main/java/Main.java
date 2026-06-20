import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    private static String currentDir = System.getProperty("user.dir");
    private static final List<String> BUILTINS = Arrays.asList("echo", "exit", "type", "pwd", "cd");

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("$ ");
            System.out.flush();
            String input = reader.readLine();

            if (input == null) {
                break;
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
                case "type":
                    handleType(commandArgs);
                    break;
                case "exit":
                    int code = commandArgs.isEmpty() ? 0 :