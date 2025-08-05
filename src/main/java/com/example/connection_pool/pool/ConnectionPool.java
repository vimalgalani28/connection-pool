package com.example.connection_pool.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread-safe connection pool implementation with retry logic, in-use tracking,
 * and defensive connection validation.
 * <p>
 * Features:
 * - Pre-initialized pool with minConnections
 * - Enforces max connection limit
 * - Retries getConnection up to 3 times if pool is exhausted
 * - Prevents double-release using inUse tracking
 * - Validates connection before reuse or release
 */
@Component
public class ConnectionPool {
    private final String url;
    private final String userName;
    private final String password;
    private final Integer maxConnections;

    private final Integer MAX_RETRIES = 3;

    private final Queue<Connection> connectionQueue;
    private AtomicInteger connectionsPresent;
    private Set<Connection> inUseConnections;

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPool.class);

    public ConnectionPool(String url, String userName, String password, Integer minConnections, Integer maxConnections) {
        if (url == null || userName == null || password == null || minConnections == null || maxConnections == null || minConnections > maxConnections) {
            throw new IllegalStateException("Invalid input to create connection pool");
        }
        LOG.info("Creating Connection Pool for url: {}", url);
        this.url = url;
        this.userName = userName;
        this.password = password;
        this.maxConnections = maxConnections;
        this.connectionsPresent = new AtomicInteger();
        inUseConnections = ConcurrentHashMap.newKeySet();

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

    /**
     * Attempts to retrieve a valid connection from the pool.
     * If none are available, tries to create a new one (within max limit).
     * Retries up to MAX_RETRIES times with 100ms delay if the pool is exhausted.
     *
     * @return a valid and tracked Connection
     * @throws RuntimeException if no connection is available after retries
     */
    public Connection getConnection() {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            Connection connection = connectionQueue.poll();
            if (connection != null && isValid(connection)) {
                inUseConnections.add(connection);
                return connection;
            }
            while (true) {
                int current = connectionsPresent.get();
                if (current >= maxConnections) {
                    break;
                }
                if (connectionsPresent.compareAndSet(current, current + 1)) {
                    connection = createConnection();
                    inUseConnections.add(connection);
                    return connection;
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

    /**
     * Releases a connection back to the pool.
     * Prevents re-adding already released or unknown connections.
     * Ignores nulls. Only valid and in-use connections are re-pooled.
     *
     * @param connection the connection to release
     * @throws RuntimeException if the connection wasn't in use or is untracked
     */
    public void releaseConnection(Connection connection) {
        if (connection == null) return;
        if (inUseConnections.remove(connection)) {
            if (isValid(connection)) {
                connectionQueue.offer(connection);
            }
            return;
        }
        throw new RuntimeException("Trying to release connection which is either already released or not created by pool");
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
        return maxConnections - inUseConnections.size();
    }

    public int getIdleConnections() {
        return connectionQueue.size();
    }
}
