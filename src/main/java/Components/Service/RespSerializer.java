package Components.Service;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class RespSerializer {
    private static final Logger logger = Logger.getLogger(RespSerializer.class.getName());

    public String serializeBulkString(String s) {
        if (s == null) {
            return "$-1\r\n";
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        return "$" + bytes.length + "\r\n" + s + "\r\n";
    }

    /**
     * Reads exactly one RESP command from a TCP stream.
     * This method deliberately does not assume that one read() == one command.
     */
    public String[] readCommand(InputStream inputStream) throws IOException {
        int first = inputStream.read();

        if (first == -1) {
            return null;
        }

        if (first != '*') {
            throw new IOException("Unsupported RESP request type: " + (char) first);
        }

        int argumentCount = Integer.parseInt(readLine(inputStream));
        if (argumentCount < 0) {
            throw new IOException("Invalid RESP array length");
        }

        String[] command = new String[argumentCount];

        for (int i = 0; i < argumentCount; i++) {
            int type = inputStream.read();
            if (type != '$') {
                throw new IOException("Expected bulk string, got: " + (char) type);
            }

            int length = Integer.parseInt(readLine(inputStream));
            if (length < 0) {
                command[i] = null;
                continue;
            }

            byte[] data = readExactly(inputStream, length);
            expectCRLF(inputStream);
            command[i] = new String(data, StandardCharsets.UTF_8);
        }

        return command;
    }

    public String readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int previous = -1;

        while (true) {
            int current = inputStream.read();

            if (current == -1) {
                if (output.size() == 0) {
                    return null;
                }
                throw new EOFException("Unexpected end of stream while reading line");
            }

            if (previous == '\r' && current == '\n') {
                byte[] bytes = output.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
            }

            output.write(current);
            previous = current;
        }
    }

    public byte[] readExactly(InputStream inputStream, int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;

        while (offset < length) {
            int read = inputStream.read(result, offset, length - offset);
            if (read == -1) {
                throw new EOFException("Unexpected end of stream");
            }
            offset += read;
        }

        return result;
    }

    public void expectCRLF(InputStream inputStream) throws IOException {
        int cr = inputStream.read();
        int lf = inputStream.read();

        if (cr != '\r' || lf != '\n') {
            throw new IOException("Invalid RESP line ending");
        }
    }

    // Kept for compatibility with the existing tests/code.
    public int getParts(char[] dataArr, int i, String[] subArray) {
        int j = 0;
        while (i < dataArr.length && j < subArray.length) {
            if (dataArr[i] == '$') {
                i++;
                StringBuilder partLength = new StringBuilder();
                while (i < dataArr.length && Character.isDigit(dataArr[i])) {
                    partLength.append(dataArr[i++]);
                }
                i += 2;
                int length = Integer.parseInt(partLength.toString());
                StringBuilder part = new StringBuilder();
                for (int k = 0; k < length && i < dataArr.length; k++) {
                    part.append(dataArr[i++]);
                }
                i += 2;
                subArray[j++] = part.toString();
            } else {
                i++;
            }
        }
        return i;
    }

    // Kept for compatibility with the existing tests.
    public List<String[]> deseralize(byte[] command) {
        List<String[]> result = new ArrayList<>();

        if (command == null || command.length == 0) {
            return result;
        }

        try {
            int index = 0;

            while (index < command.length) {
                while (index < command.length &&
                        (command[index] == 0 || command[index] == '\r' || command[index] == '\n')) {
                    index++;
                }

                if (index >= command.length) break;

                if (command[index] != '*') {
                    index++;
                    continue;
                }

                int[] parsed = parseLegacyArray(command, index, result);
                if (parsed[0] <= index) break;
                index = parsed[0];
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "RESP deserialize error: " + e.getMessage());
        }

        return result;
    }

    private int[] parseLegacyArray(byte[] data, int start, List<String[]> result)
            throws IOException {

        int index = start + 1;
        int lineEnd = findCRLF(data, index);

        if (lineEnd < 0) {
            return new int[]{start, 0};
        }

        int count = parseInteger(data, index, lineEnd);
        index = lineEnd + 2;

        if (count < 0) {
            return new int[]{index, 0};
        }

        // Compatibility with the original project's test format:
        // *2\r\n followed by two nested *3 RESP commands.
        if (index < data.length && data[index] == '*') {
            int addedBefore = result.size();

            for (int i = 0; i < count && index < data.length; i++) {
                if (data[index] != '*') break;

                int before = result.size();
                int[] nested = parseLegacyArray(data, index, result);

                if (nested[0] <= index || result.size() == before) break;

                index = nested[0];
            }

            return new int[]{index, result.size() - addedBefore};
        }

        String[] values = new String[count];

        for (int i = 0; i < count; i++) {
            if (index >= data.length || data[index] != '$') {
                throw new IOException("Expected bulk string");
            }

            index++;
            lineEnd = findCRLF(data, index);

            if (lineEnd < 0) {
                throw new IOException("Missing bulk-string length CRLF");
            }

            int length = parseInteger(data, index, lineEnd);
            index = lineEnd + 2;

            if (length == -1) {
                values[i] = null;
                continue;
            }

            if (length < -1 || index + length > data.length) {
                throw new IOException("Invalid bulk-string length");
            }

            values[i] = new String(
                    data, index, length, StandardCharsets.UTF_8
            );
            index += length;

            if (index + 1 < data.length &&
                    data[index] == '\r' &&
                    data[index + 1] == '\n') {
                index += 2;
            } else if (i < count - 1) {
                throw new IOException("Missing CRLF");
            }
        }

        result.add(values);
        return new int[]{index, 1};
    }

    private int findCRLF(byte[] data, int from) {
        for (int i = from; i + 1 < data.length; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n') return i;
        }
        return -1;
    }

    private int parseInteger(byte[] data, int start, int end) {
        return Integer.parseInt(new String(
                data, start, end - start, StandardCharsets.UTF_8
        ).trim());
    }

    public String respInteger(int i) {
        return ":" + i + "\r\n";
    }

    public String respArray(String[] command) {
        StringBuilder result = new StringBuilder();
        result.append('*').append(command.length).append("\r\n");

        for (String value : command) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            result.append('$').append(bytes.length).append("\r\n");
            result.append(value).append("\r\n");
        }

        return result.toString();
    }

    public String respArray(List<String> command) {
        StringBuilder result = new StringBuilder();
        result.append('*').append(command.size()).append("\r\n");
        for (String value : command) {
            result.append(value);
        }
        return result.toString();
    }

    public String[] parseArray(String[] parts) {
        int length = Integer.parseInt(parts[0]);
        String[] command = new String[length];
        command[0] = parts[2];

        int idx = 1;
        for (int i = 4; i < parts.length && idx < length; i += 2) {
            command[idx++] = parts[i];
        }
        return command;
    }
}
