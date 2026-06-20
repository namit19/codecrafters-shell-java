import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));

        Path currentDirectory = Paths.get(System.getProperty("user.dir"));

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

            String[] parts = input.split("\\s+");
            String command = parts[0];

            switch (command) {

                case "echo":
                    if (parts.length > 1) {
                        System.out.println(input.substring(5));
                    } else {
                        System.out.println();
                    }
                    break;

                case "exit":
                    if (parts.length > 1 && parts[1].equals("0")) {
                        return;
                    }
                    break;

                case "type":
                    if (parts.length < 2) {
                        break;
                    }

                    String cmd = parts[1];

                    if (cmd.equals("echo")
                            || cmd.equals("exit")
                            || cmd.equals("type")
                            || cmd.equals("pwd")
                            || cmd.equals("cd")) {
                        System.out.println(cmd + " is a shell builtin");
                    } else {
                        System.out.println(cmd + ": not found");
                    }
                    break;

                case "pwd":
                    System.out.println(currentDirectory);
                    break;

                case "cd":
                    String pathStr = parts.length > 1 ? parts[1] : "";

                    if (pathStr.equals("~")) {
                        pathStr = System.getenv("HOME");
                    }

                    Path targetPath = Paths.get(pathStr);

                    if (!targetPath.isAbsolute()) {
                        targetPath = currentDirectory.resolve(targetPath);
                    }

                    targetPath = targetPath.normalize();

                    if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
                        currentDirectory = targetPath;
                    } else {
                        System.out.println(
                                "cd: " + pathStr + ": No such file or directory");
                    }
                    break;

                default:
                    System.out.println(command + ": command not found");
            }
        }
    }
}