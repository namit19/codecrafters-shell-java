import java.io.File;
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
                if (parts.length < 2) {
                    continue;
                }

                String target = parts[1];

                if (target.equals("echo") ||
                    target.equals("exit") ||
                    target.equals("type")) {
                    System.out.println(target + " is a shell builtin");
                    continue;
                }

                String pathEnv = System.getenv("PATH");
                String[] paths = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String dir : paths) {
                    File file = new File(dir, target);

                    if (file.exists() && file.isFile() && file.canExecute()) {
                        System.out.println(target + " is " + file.getAbsolutePath());
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    System.out.println(target + ": not found");
                }

                continue;
            }

            System.out.println(command + ": command not found");
        }

        scanner.close();
    }
}