import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PipelineHandler {

    public static void executePipeline(String userInput) {
        // 1. Split the command by the pipe character
        String[] pipeParts = userInput.split("\\|");
        if (pipeParts.length != 2) {
            System.err.println("This implementation only supports two-stage pipelines.");
            return;
        }

        // Clean up whitespace and tokenize arguments
        List<String> cmd1Args = parseCommand(pipeParts[0].trim());
        List<String> cmd2Args = parseCommand(pipeParts[1].trim());

        try {
            // 2. Start the first process (e.g., cat or tail -f)
            ProcessBuilder pb1 = new ProcessBuilder(cmd1Args);
            // Redirect error to standard err so it doesn't pollute the pipe
            pb1.redirectError(ProcessBuilder.Redirect.INHERIT); 
            Process p1 = pb1.start();

            // 3. Start the second process (e.g., wc or head)
            ProcessBuilder pb2 = new ProcessBuilder(cmd2Args);
            pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
            // The final output needs to go to the user's console
            pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT); 
            Process p2 = pb2.start();

            // 4. Create a background thread to pump data from P1 to P2
            Thread pipeThread = new Thread(() -> {
                try (InputStream inputFromP1 = p1.getInputStream();
                     OutputStream outputToP2 = p2.getOutputStream()) {
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    // Continuously read from p1 and write to p2
                    while ((bytesRead = inputFromP1.read(buffer)) != -1) {
                        outputToP2.write(buffer, 0, bytesRead);
                        outputToP2.flush(); // Crucial for real-time streaming like tail -f
                    }
                } catch (Exception e) {
                    // Handle or suppress broken pipe exceptions when p2 closes early (like 'head -n 5')
                }
            });
            pipeThread.start();

            // 5. Wait for the processes to finish
            int p1ExitCode = p1.waitFor();
            
            // Wait for P1's stream to finish pumping, then close P2's input so it knows no more data is coming
            pipeThread.join(); 
            p2.getOutputStream().close(); 

            int p2ExitCode = p2.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper method to split command strings by spaces while ignoring extra spaces
    private static List<String> parseCommand(String command) {
        return new ArrayList<>(Arrays.asList(command.split("\\s+")));
    }
}