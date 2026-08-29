package Components.Server;

import Components.Infra.Client;
import Components.Infra.ConnectionPool;
import Components.Service.CommandHandler;
import Components.Service.RespSerializer;
import Components.Service.ResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SlaveTcpServer {
    private static final Logger logger = Logger.getLogger(SlaveTcpServer.class.getName());

    @Autowired
    private RespSerializer respSerializer;

    @Autowired
    private CommandHandler commandHandler;

    @Autowired
    private RedisConfig redisConfig;

    @Autowired
    private ConnectionPool connectionPool;

    public void startServer() {
        int port = redisConfig.getPort();

        System.out.println("Starting Redis slave server on port: " + port);
        System.out.println("Connecting to master: "
                + redisConfig.getMasterHost() + ":" + redisConfig.getMasterPort());

        CompletableFuture.runAsync(this::initiateSlavery);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            int id = 0;

            while (true) {
                Socket clientSocket = serverSocket.accept();
                id++;

                Client client = new Client(
                        clientSocket,
                        clientSocket.getInputStream(),
                        clientSocket.getOutputStream(),
                        id
                );

                CompletableFuture.runAsync(() -> handleClientSafely(client));
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server error: " + e.getMessage());
        }
    }

    private void handleClientSafely(Client client) {
        try {
            handleClient(client);
        } catch (IOException e) {
            logger.log(Level.FINE, "Replica client disconnected: " + e.getMessage());
        } finally {
            connectionPool.removeClient(client);
            client.close();
        }
    }

    /**
     * Connect to the master and keep this socket open for the entire lifetime
     * of the replica. After the initial RDB payload, this method continuously
     * reads RESP commands from the replication stream.
     */
    private void initiateSlavery() {
        try (Socket master = new Socket(
                redisConfig.getMasterHost(),
                redisConfig.getMasterPort())) {

            master.setKeepAlive(true);

            InputStream inputStream = master.getInputStream();
            OutputStream outputStream = master.getOutputStream();

            // 1. PING
            send(outputStream, "*1\r\n$4\r\nPING\r\n");
            String response = respSerializer.readLine(inputStream);
            System.out.println("Master PING response: " + response + "\r\n");

            // 2. REPLCONF listening-port
            String listeningPort = String.valueOf(redisConfig.getPort());
            send(outputStream, respSerializer.respArray(new String[]{
                    "REPLCONF", "listening-port", listeningPort
            }));
            response = respSerializer.readLine(inputStream);
            System.out.println("REPLCONF listening-port response: " + response + "\r\n");

            // 3. REPLCONF capa psync2
            send(outputStream, respSerializer.respArray(new String[]{
                    "REPLCONF", "capa", "psync2"
            }));
            response = respSerializer.readLine(inputStream);
            System.out.println("REPLCONF capa response: " + response + "\r\n");

            // 4. PSYNC
            System.out.println("PSYNC sent to master");
            send(outputStream, respSerializer.respArray(new String[]{
                    "PSYNC", "?", "-1"
            }));

            // 5. FULLRESYNC line
            String fullResync = respSerializer.readLine(inputStream);
            if (fullResync == null || !fullResync.startsWith("+FULLRESYNC")) {
                throw new IOException("Invalid PSYNC response: " + fullResync);
            }

            System.out.println("PSYNC response: " + fullResync + "\r\n");

            String[] fullResyncParts = fullResync.split("\\s+");
            if (fullResyncParts.length >= 3) {
                redisConfig.setMasterReplId(fullResyncParts[1]);
                redisConfig.setMasterReplOffset(Long.parseLong(fullResyncParts[2]));
            }

            // 6. RDB bulk string header
            int dollar = inputStream.read();
            if (dollar != '$') {
                throw new IOException("Expected RDB bulk string, got: " + (char) dollar);
            }

            int rdbLength = Integer.parseInt(respSerializer.readLine(inputStream));
            byte[] rdb = respSerializer.readExactly(inputStream, rdbLength);

            System.out.println("RDB header: $" + rdbLength);
            System.out.println("RDB payload consumed: " + rdb.length + " bytes");
            System.out.println("Replication stream started");

            // 7. Continuous replication stream.
            while (!master.isClosed()) {
                String[] command = respSerializer.readCommand(inputStream);

                if (command == null) {
                    break;
                }

                if (command.length == 0 || command[0] == null) {
                    continue;
                }

                command[0] = command[0].toUpperCase();

                System.out.println(
                        "Replication command: " + String.join(" ", command)
                );

                processReplicationCommand(command, master, outputStream);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Replication connection failed: " + e.getMessage());
        }
    }

    private void processReplicationCommand(
            String[] command,
            Socket masterSocket,
            OutputStream masterOutputStream
    ) throws IOException {

        String commandName = command[0].toUpperCase();

        // Use the same CommandHandler/store used by the local replica.
        // Do not send the normal command response back to the master for
        // ordinary replicated writes.
        switch (commandName) {
            case "SET":
                commandHandler.set(command);
                break;

            case "INCR":
                commandHandler.incr(command);
                break;

            case "REPLCONF":
                if (command.length >= 2 && "GETACK".equals(command[1].toUpperCase())) {
                    String ack = commandHandler.replconf(command,
                            new Client(masterSocket,
                                    masterSocket.getInputStream(),
                                    masterOutputStream,
                                    -1));
                    if (ack != null && !ack.isEmpty()) {
                        send(masterOutputStream, ack);
                    }
                }
                break;

            default:
                System.out.println(
                        "Replication command ignored: " + String.join(" ", command)
                );
                break;
        }
    }

    private void send(OutputStream outputStream, String data) throws IOException {
        outputStream.write(data.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private void handleClient(Client client) throws IOException {
        connectionPool.addClient(client);

        while (!client.socket.isClosed()) {
            String[] command = respSerializer.readCommand(client.inputStream);

            if (command == null) {
                break;
            }

            if (command.length == 0 || command[0] == null) {
                continue;
            }

            command[0] = command[0].toUpperCase();
            handleCommand(command, client);
        }
    }

    private void handleCommand(String[] command, Client client) throws IOException {
        String res;
        byte[] data = null;

        switch (command[0]) {
            case "PING":
                res = commandHandler.ping(command);
                break;

            case "ECHO":
                res = commandHandler.echo(command);
                break;

            case "SET":
                res = "-READONLY You can't write against a replica.\r\n";
                break;

            case "GET":
                res = commandHandler.get(command);
                break;

            case "INFO":
                res = commandHandler.info(command);
                break;

            case "PSYNC":
                ResponseDto responseDto = commandHandler.psync(command);
                res = responseDto.response;
                data = responseDto.data;
                break;

            case "WAIT":
                if (connectionPool.bytesSentToSlaves == 0) {
                    res = respSerializer.respInteger(connectionPool.slavesThatAreCaughtUp);
                } else {
                    res = commandHandler.wait(command, java.time.Instant.now());
                    connectionPool.slavesThatAreCaughtUp = 0;
                }
                break;

            default:
                res = "-ERR unknown command '" + command[0] + "'\r\n";
                break;
        }

        client.send(res, data);
    }
}
