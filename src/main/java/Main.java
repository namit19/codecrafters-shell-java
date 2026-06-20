import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class Main {

    private static File currentDirectory = new File(System.getProperty("user.dir"));

    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String input = scanner.nextLine();

            if (input.isEmpty()) {
                continue;
            }

            String[] parts = input.split(" ");
            String command = parts[0];

            if (command.equals("exit")) {
                break;
            }

            if (command.equals("echo")) {
                if (parts.length > 1) {
                    System.out.println(input.substring(5));
                } else {
                    System.out.println();
                }
                continue;
            }

            if (command.equals("pwd")) {
                System.out.println(currentDirectory.getAbsolutePath());
                continue;
            }

            if (command.equals("cd")) {
                if (parts.length < 2) {
                    continue;
                }

                File newDir = new File(parts[1]);

                if (newDir.exists() && newDir.isDirectory()) {
                    currentDirectory = newDir.getAbsoluteFile();
                } else {
                    System.out.println("cd: " + parts[1] + ": No such file or directory");
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
                    target.equals("type") ||
                    target.equals("pwd") ||
                    target.equals("cd")) {
                    System.out.println(target + " is a shell builtin");
                    continue;
                }

                String executable = findExecutable(target);

                if (executable != null) {
                    System.out.println(target + " is " + executable);
                } else {
                    System.out.println(target + ": not found");
                }
                continue;
            }

            String executable = findExecutable(command);

            if (executable != null) {
                ProcessBuilder pb = new ProcessBuilder(parts);
                pb.directory(currentDirectory);
                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                Process process = pb.start();
                process.waitFor();
            } else {
                System.out.println(command + ": command not found");
            }
        }

        scanner.close();
    }

    private static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) {
            return null;
        }

        String[] paths = pathEnv.split(File.pathSeparator);

        for (String dir : paths) {
            File file = new File(dir, command);

            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }
}