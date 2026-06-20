import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // Corrected from System.util.in to System.in
        Scanner scanner = new Scanner(System.in);

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
                        // Added "jobs" to the recognized builtins list
                        if (target.equals("jobs") || target.equals("exit") || target.equals("type")) {
                            System.out.println(target + " is a shell builtin");
                        } else {
                            System.out.println(target + ": not found");
                        }
                    }
                    break;

                case "jobs":
                    // Stage requirement: Empty implementation.
                    // Absolutely nothing is printed, passing control back to the loop.
                    break;

                default:
                    System.out.println(command + ": command not found");
                    break;
            }
        }
        scanner.close();
    }
}