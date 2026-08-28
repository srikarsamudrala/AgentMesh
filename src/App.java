import java.util.Arrays;

public class App {
    /**
     * Simple launcher for either the server or client.
     * Usage: java App server [port]
     *        java App client [host] [port]
     *        java App analytics [host] [port]
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            switch (args[0]) {
                case "server":
                    String[] serverArgs = Arrays.copyOfRange(args, 1, args.length);
                    server.ChatServer.main(serverArgs);
                    break;
                case "client":
                    String[] clientArgs = Arrays.copyOfRange(args, 1, args.length);
                    client.ChatClient.main(clientArgs);
                    break;
                case "analytics":
                    String[] analyticsArgs = Arrays.copyOfRange(args, 1, args.length);
                    client.AnalyticsClient.main(analyticsArgs);
                    break;
                default:
                    printUsage();
            }
        } else {
            printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java App server [port]");
        System.out.println("       java App client [host] [port]");
        System.out.println("       java App analytics [host] [port]");
    }
}
