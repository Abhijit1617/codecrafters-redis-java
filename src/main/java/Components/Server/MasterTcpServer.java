package Components.Server;

import Components.Infra.Client;
import Components.Infra.ConnectionPool;
import Components.Infra.Slave;
import Components.Repository.Store;
import Components.Repository.Value;
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
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class MasterTcpServer {
    private static final Logger logger = Logger.getLogger(MasterTcpServer.class.getName());

    @Autowired
    private RespSerializer respSerializer;

    @Autowired
    private CommandHandler commandHandler;

    @Autowired
    private RedisConfig redisConfig;

    @Autowired
    private ConnectionPool connectionPool;

    @Autowired
    private Store store;

    public void startServer() {
        int port = redisConfig.getPort();

        System.out.println("Starting Redis master server on port: " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            int id = 0;

            while (true) {
                Socket socket = serverSocket.accept();
                id++;

                InputStream inputStream = socket.getInputStream();
                OutputStream outputStream = socket.getOutputStream();
                Client client = new Client(socket, inputStream, outputStream, id);

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
            logger.log(Level.FINE, "Client disconnected: " + e.getMessage());
        } finally {
            connectionPool.removeClient(client);
            connectionPool.removeSlave(client);
            client.close();
        }
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
        if (!client.getTransactionalContext()) {
            ResponseDto responseDto = caseHandler(command, client);
            client.send(responseDto);
        } else if (!isTransactionalControlCommand(command[0])) {
            addCommandToTransaction(command, client);
        } else {
            transactionController(command, client);
        }
    }

    private void transactionController(String[] command, Client client) throws IOException {
        switch (command[0]) {
            case "EXEC":
                if (client.commandQueue == null || client.commandQueue.isEmpty()) {
                    client.send("*0\r\n");
                    client.endTransaction();
                    return;
                }

                Queue<String[]> commands = new LinkedList<>(client.commandQueue);
                BiFunction<String[], Map<String, Value>, String> transactionCacheApplier =
                        commandHandler.getTransactionCommandCacheApplier();

                store.executeTransaction(client, transactionCacheApplier);
                client.endTransaction();

                while (!commands.isEmpty()) {
                    String[] commandToPropagate = commands.poll();
                    propagate(commandToPropagate);
                }

                client.send(respSerializer.respArray(client.transactionResponse));
                break;

            case "DISCARD":
                client.endTransaction();
                client.send("+OK\r\n");
                break;

            default:
                client.send("-ERR unknown transaction command\r\n");
        }
    }

    private void addCommandToTransaction(String[] command, Client client) throws IOException {
        client.commandQueue.offer(command);
        client.send("+QUEUED\r\n");
    }

    private boolean isTransactionalControlCommand(String command) {
        return "EXEC".equals(command) || "DISCARD".equals(command);
    }

    public ResponseDto caseHandler(String[] command, Client client) {
        String commandName = command[0].toUpperCase();
        String res = "";
        byte[] data = null;

        switch (commandName) {
            case "PING":
                res = commandHandler.ping(command);
                break;

            case "EXEC":
                res = "-ERR EXEC without MULTI\r\n";
                break;

            case "DISCARD":
                res = "-ERR DISCARD without MULTI\r\n";
                break;

            case "MULTI":
                client.beginTransaction();
                res = "+OK\r\n";
                break;

            case "INCR":
                res = commandHandler.incr(command);
                propagateIfWriteSucceeded(command, res);
                break;

            case "ECHO":
                res = commandHandler.echo(command);
                break;

            case "SET":
                res = commandHandler.set(command);
                propagateIfWriteSucceeded(command, res);
                break;

            case "GET":
                res = commandHandler.get(command);
                break;

            case "INFO":
                res = commandHandler.info(command);
                break;

            case "REPLCONF":
                res = commandHandler.replconf(command, client);
                break;

            case "WAIT":
                if (connectionPool.bytesSentToSlaves == 0) {
                    res = respSerializer.respInteger(connectionPool.slavesThatAreCaughtUp);
                    break;
                }
                Instant start = Instant.now();
                res = commandHandler.wait(command, start);
                connectionPool.slavesThatAreCaughtUp = 0;
                break;

            case "PSYNC":
                ResponseDto responseDto = commandHandler.psync(command);
                res = responseDto.response;
                data = responseDto.data;
                break;

            default:
                res = "-ERR unknown command '" + commandName + "'\r\n";
                break;
        }

        return new ResponseDto(res, data);
    }

    private void propagateIfWriteSucceeded(String[] command, String response) {
        if (response != null && response.startsWith("+OK")) {
            propagate(command);
        } else if ("INCR".equals(command[0]) && response != null && response.startsWith(":")) {
            propagate(command);
        }
    }

    /**
     * Send the original RESP command to every connected replica.
     * The replica socket remains open after this write.
     */
    private void propagate(String[] command) {
        String commandRespString = respSerializer.respArray(command);
        byte[] bytes = commandRespString.getBytes(StandardCharsets.UTF_8);

        System.out.println("Replication command: " + String.join(" ", command));
        System.out.println("Connected replicas: " + connectionPool.getSlaves().size());

        for (Slave slave : connectionPool.getSlaves()) {
            try {
                slave.send(bytes);
                connectionPool.bytesSentToSlaves += bytes.length;
                System.out.println("Replication sent to replica: " + slave.connection.id);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not send replication command: " + e.getMessage());
                connectionPool.removeSlave(slave);
            }
        }
    }
}
