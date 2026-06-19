import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine();

            if (input.equals("exit 0")) {
                break;
            }

            String[] parts = input.split(" ", 2);
            String command = parts[0];

            if (command.equals("echo")) {
                if (parts.length > 1) {
                    System.out.println(parts[1]);
                } else {
                    System.out.println();
                }
            } else {
                System.out.println(command + ": command not found");
            }
        }

        scanner.close();
    }
}