package de.angermueller.factorio.service;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OnDemandRCONClient implements RCONClient {

    private final String host;
    private final int port;
    private final String password;
    private final Lock lock = new ReentrantLock();
    private RCONClientImpl client;

    public OnDemandRCONClient(String host, int port, String password) {
        assert host != null && !host.isEmpty();
        assert port > 0 && port < 655335;
        this.host = host;
        this.port = port;
        this.password = password;
    }

    protected RCONClient getClient() throws IOException {
        if(client == null) {
            client = new RCONClientImpl(host, port, password);
            client.init();
        }
        return client;
    }

    @PreDestroy
    public void close() throws IOException {
        lock.lock();
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String sendCommand(String command) throws IOException {
        lock.lock();
        try {
            return getClient().sendCommand(command);
        } catch (IOException e) {
            client = null;
            throw e;
        } finally {
            lock.unlock();
        }
    }
}
