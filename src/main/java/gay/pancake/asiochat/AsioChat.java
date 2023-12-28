package gay.pancake.asiochat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Dead simple main class
 *
 * @author Pancake
 */
public class AsioChat {

    public static void main(String[] args) throws Exception {
        Files.copy(Objects.requireNonNull(AsioChat.class.getResourceAsStream("/jasiohost64.dll")), Path.of("jasiohost64.dll"), StandardCopyOption.REPLACE_EXISTING);

        if (args.length == 0) {
            System.out.println("Usage: java -jar asiochat.jar --client <ip> <port> | --server <port>");
            return;
        }

        if (args[0].equals("--client")) {
            if (args.length != 3) {
                System.out.println("Usage: java -jar asiochat.jar --client <ip> <port>");
                return;
            }

            var client = new Client();
            client.start("Focusrite USB ASIO", args[1], Integer.parseInt(args[2]), 0, 0, 1);

            System.out.println("Press enter to exit...");
            while (!client.getExitCallback().isCompletedExceptionally()) {
                if (System.in.available() > 0 && System.in.read() == '\n')
                    break;
                Thread.yield();
            }

            client.stop();
        } else if (args[0].equals("--server")) {
            if (args.length != 2) {
                System.out.println("Usage: java -jar asiochat.jar --server <port>");
                return;
            }

            var server = new Server();
            server.start("Focusrite USB ASIO", Integer.parseInt(args[1]), 0, 0, 1);

            System.out.println("Press enter to exit...");
            while (!server.getExitCallback().isCompletedExceptionally()) {
                if (System.in.available() > 0 && System.in.read() == '\n')
                    break;
                Thread.yield();
            }

            server.stop();
        } else {
            System.out.println("Usage: java -jar asiochat.jar --client <ip> <port> | --server <port>");
        }

    }

}
