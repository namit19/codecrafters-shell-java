import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.util.in);

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) break;
            
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            // Simple split by whitespace to get command and arguments
            String[] parts = input.split("\\s+");
            String command = parts[0];

            switch (command) {
                case "exit":
                    System.exit(0);
                    break;

                case "type":
                    if (parts.length > 1) {
                        String target = parts[1];
                        if (target.equals("jobs") || target.equals("exit") || target.equals("type")) {
                            System.out.println(target + " is a shell builtin");
                        } else {
                            // Your existing logic for checking PATH / external commands
                            System.out.println(target + ": not found");
                        }
                    }
                    break;

                case "jobs":
                    // Stage requirement: Provide an empty implementation.
                    // Absolutely nothing is printed, and we break back to the prompt.
                    break;

                default:
                    // Your logic for executing external commands
                    System.out.println(command + ": command not found");
                    break;
            }
        }
        scanner.close();
    }
}