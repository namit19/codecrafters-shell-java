import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine();

            String[] parts = input.split(" ", 2);
            String command = parts[0];

            if (command.equals("exit")) {
                break;
            }

            if (command.equals("echo")) {
                if (parts.length > 1) {
                    System.out.println(parts[1]);
                } else {
                    System.out.println();
                }
                continue;
            }

            if (command.equals("type")) {
                if (parts.length > 1) {
                    String arg = parts[1];

                    if (arg.equals("echo") ||
                        arg.equals("exit") ||
                        arg.equals("type")) {
                        System.out.println(arg + " is a shell builtin");
                    } else {
                        System.out.println(arg + ": not found");
                    }
                }
                continue;
            }

            System.out.println(command + ": command not found");
        }

        scanner.close();
    }
}