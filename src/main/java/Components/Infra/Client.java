package Components.Infra;

import Components.Service.ResponseDto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Client {
    public Socket socket;
    public InputStream inputStream;
    public OutputStream outputStream;
    public int id;

    private boolean transactionalContext;
    public Queue<String[]> commandQueue;
    public List<String> transactionResponse;

    public boolean getTransactionalContext() {
        return transactionalContext;
    }

    public boolean beginTransaction() {
        if (transactionalContext) {
            return false;
        }
        transactionalContext = true;
        transactionResponse = new ArrayList<>();
        commandQueue = new LinkedList<>();
        return true;
    }

    public void endTransaction() {
        commandQueue = null;
        transactionalContext = false;
    }

    public Client(Socket socket, InputStream inputStream, OutputStream outputStream, int id) {
        this.socket = socket;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.id = id;
    }

    public synchronized void send(String res, byte[] data) throws IOException {
        if (res != null && !res.isEmpty()) {
            outputStream.write(res.getBytes(StandardCharsets.UTF_8));
        }
        if (data != null) {
            outputStream.write(data);
        }
        outputStream.flush();
    }

    public synchronized void send(ResponseDto res) throws IOException {
        if (res == null) {
            return;
        }
        if (res.response != null && !res.response.isEmpty()) {
            outputStream.write(res.response.getBytes(StandardCharsets.UTF_8));
        }
        if (res.data != null) {
            outputStream.write(res.data);
        }
        outputStream.flush();
    }

    public synchronized void send(byte[] data) throws IOException {
        if (data != null) {
            outputStream.write(data);
            outputStream.flush();
        }
    }

    public synchronized void send(String data) throws IOException {
        if (data != null && !data.isEmpty()) {
            outputStream.write(data.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
    }

    public String readLine() throws IOException {
        StringBuilder builder = new StringBuilder();
        int previous = -1;

        while (true) {
            int current = inputStream.read();
            if (current == -1) {
                return builder.length() == 0 ? null : builder.toString();
            }
            if (previous == '\r' && current == '\n') {
                builder.setLength(builder.length() - 1);
                return builder.toString();
            }
            builder.append((char) current);
            previous = current;
        }
    }

    public String readExactString(int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = inputStream.read(data, offset, length - offset);
            if (read == -1) {
                throw new IOException("Connection closed while reading data");
            }
            offset += read;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
