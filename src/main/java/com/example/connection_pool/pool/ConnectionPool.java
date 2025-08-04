package com.example.connection_pool.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ConnectionPool {
    private final String url;
    private final String userName;
    private final String password;
    private final Integer maxConnections;

    private final Integer MAX_RETRIES = 3;

    private final Queue<Connection> connectionQueue;
    private AtomicInteger connectionsPresent;

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPool.class);

    public ConnectionPool(String url, String userName, String password, Integer minConnections, Integer maxConnections) {
        LOG.info("Creating Connection Pool for url: {}", url);
        this.url = url;
        this.userName = userName;
        this.password = password;
        this.maxConnections = maxConnections;
        this.connectionsPresent = new AtomicInteger();

        connectionQueue = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < minConnections; i++) {
            connectionsPresent.incrementAndGet();
            connectionQueue.offer(createConnection());
        }
    }

    private Connection createConnection() {
        try {
            return DriverManager.getConnection(this.url, this.userName, this.password);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Connection getConnection() {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            Connection connection = connectionQueue.poll();
            if (connection != null && isValid(connection)) {
                return connection;
            }
            while (true) {
                int current = connectionsPresent.get();
                if (current >= maxConnections) {
                    break;
                }
                if (connectionsPresent.compareAndSet(current, current + 1)) {
                    return createConnection();
                }
            }

            if (attempt < MAX_RETRIES) {
                waitForNextAttempt();
            }
        }
        throw new RuntimeException("Max Resources Utilized");
    }

    private void waitForNextAttempt() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting for connection", e);
        }
    }

    public void releaseConnection(Connection connection) {
        if (connection != null && isValid(connection)) {
            connectionQueue.offer(connection);
        }
    }

    private boolean isValid(Connection connection) {
        try {
            if (!connection.isClosed()) {
                return true;
            } else {
                connectionsPresent.decrementAndGet();
            }
        } catch (Exception ex) {
            connectionsPresent.decrementAndGet();
            LOG.warn("Connection validation failed. Connection will be discarded.", ex);
        }
        return false;
    }

    public int getAvailableConnections() {
        return maxConnections - connectionsPresent.get();
    }
}
