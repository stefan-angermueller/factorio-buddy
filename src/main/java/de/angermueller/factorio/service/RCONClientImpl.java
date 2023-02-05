package de.angermueller.factorio.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

@Slf4j
public class RCONClientImpl implements Closeable, RCONClient {

    private int lastId = 0;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private final String password;
    private final String host;
    private final int port;

    public RCONClientImpl(String host, int port, String password) {
        assert host != null && !host.isEmpty();
        assert port > 0 && port < 65535;
        this.host = host;
        this.port = port;
        this.password = password;
    }

    @PostConstruct
    public void init() throws IOException {
        this.socket = new Socket();
        this.socket.setSoTimeout(1000);
        log.trace("Connecting to {}:{}", host, port);
        this.socket.connect(new InetSocketAddress(host, port), 1000);
        this.in = new BufferedInputStream(socket.getInputStream());
        this.out = new BufferedOutputStream(socket.getOutputStream());
        log.debug("Connected to {}:{}", host, port);
        if(password != null) {
            log.trace("Sending authentication");
            authenticate(password);
            log.debug("Authenticated");
        }
    }

    protected RCONPackage sendPackage(RCONPackage rconPackage) throws IOException {
        if(socket == null) {
            throw new IllegalStateException("Not initialised");
        }
        log.debug("Sending {}", rconPackage);
        rconPackage.write(out);
        out.flush();
        final RCONPackage response = RCONPackage.read(in, true);
        log.debug("Received {}", response);
        return response;
    }

    public void authenticate(String rconPassword) throws IOException {
        final RCONPackage authRequest = new RCONPackage(++lastId, RCONPackage.Type.SERVERDATA_AUTH, rconPassword);
        final RCONPackage response = sendPackage(authRequest);
        if(response.getId() != authRequest.getId()) {
            throw new IOException("Auth failed");
        }
    }

    @Override
    public String sendCommand(String command) throws IOException {
        final RCONPackage request = new RCONPackage(++lastId, RCONPackage.Type.SERVERDATA_EXECCOMMAND, command);
        final RCONPackage response = sendPackage(request);
        if(response.getId() != request.getId()) {
            throw new IOException("Id mismatch: Got " + response.getId() + " vs. expected " + request.getId());
        }
        return response.getBody();
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }

    @Getter
    public static class RCONPackage {

        private final int id;
        private final Type type;
        private final String body;

        public RCONPackage(int id, Type type, String body) {
            this.id = id;
            this.type = type;
            this.body = body;
        }

        public void write(OutputStream out) throws IOException {
            final byte[] body = this.body.getBytes(StandardCharsets.US_ASCII);
            final ByteBuffer buffer = ByteBuffer.allocate(body.length + 14);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(buffer.capacity()-4);
            buffer.putInt(id);
            buffer.putInt(type.getValue());
            buffer.put(body);
            buffer.put((byte) 0x00);
            buffer.put((byte) 0x00);
            out.write(buffer.array());
        }

        @Override
        public String toString() {
            String body;
            if(this.body.length() > 61) {
                body = this.body.substring(0, 61) + "... (" + this.body.length() + " chars)";
            } else {
                body = this.body;
            }
            return "RCONPackage{" +
                    "id=" + id +
                    ", type=" + type +
                    ", body='" + body.replace("\r", "<CR>").replace("\n", "<LF>") + '\'' +
                    '}';
        }

        private static ByteBuffer readBuffer(InputStream in, int size) throws IOException {
            final byte[] buf = in.readNBytes(size);
            if(buf.length != size) {
                throw new EOFException();
            }
            return ByteBuffer.wrap(buf);
        }

        public static RCONPackage read(InputStream in, boolean response) throws IOException {
            final ByteBuffer sizeBuffer = readBuffer(in, 4);
            sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
            final int size = sizeBuffer.getInt();
            final ByteBuffer buffer = readBuffer(in, size);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            final int id = buffer.getInt();
            final int typeValue = buffer.getInt();
            final byte[] body = new byte[size-10];
            buffer.get(body);
            if((buffer.get() & 0xFF) != 0x00) {
                throw new IOException("Expecting zero body terminator");
            }
            if((buffer.get() & 0xFF) != 0x00) {
                throw new IOException("Expecting zero package terminator");
            }
            return new RCONPackage(id, Type.getType(typeValue, response), new String(body, StandardCharsets.US_ASCII));
        }

        public enum Type {
            SERVERDATA_AUTH(3, false), SERVERDATA_AUTH_RESPONSE(2, true), SERVERDATA_EXECCOMMAND(2, false), SERVERDATA_RESPONSE_VALUE(0, true);

            private final int value;
            private final boolean response;

            Type(int value, boolean response) {
                this.value = value;
                this.response = response;
            }

            public int getValue() {
                return value;
            }

            public boolean isResponse() {
                return response;
            }

            public static Type getType(int value, boolean response) {
                for (Type type : Type.values()) {
                    if(type.getValue() == value && type.isResponse() == response) {
                        return type;
                    }
                }
                throw new IllegalArgumentException("Found no type for value " + value + " (response: " + response + ")");
            }

        }

    }

}
