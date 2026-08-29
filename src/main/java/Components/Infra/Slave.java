package Components.Infra;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Slave {
    public Client connection;
    public List<String> capabilities;

    public Slave(Client client) {
        this.connection = client;
        this.capabilities = new ArrayList<>();
    }

    public synchronized void send(byte[] bytes) throws IOException {
        if (bytes != null) {
            connection.outputStream.write(bytes);
            connection.outputStream.flush();
        }
    }
}
