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

            if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            } else {
                String command = input.split(" ")[0];
                System.out.println(command + ": command not found");
            }
        }
    }
}