import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine();
            String command = input.split(" ")[0];

            System.out.println(command + ": command not found");
        }
    }
}
