package com.example.connection_pool.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Queue;

@Component
public class ConnectionPool {
    private String url;
    private String userName;
    private String password;
    private Integer maxConnections;

    Queue<Connection> connectionQueue;
    private Integer connectionsPresent;

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionPool.class);

    public ConnectionPool(String url, String userName, String password, Integer minConnections, Integer maxConnections) {
        LOG.info("Creating Connection Pool for url: {}", url);
        this.url = url;
        this.userName = userName;
        this.password = password;
        this.maxConnections = maxConnections;
        this.connectionsPresent = 0;

        connectionQueue = new LinkedList<>();

        for (int i = 0; i < minConnections; i++) {
            connectionQueue.offer(createConnection());
            connectionsPresent++;
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
        Connection connection = connectionQueue.poll();
        if (connection != null) {
            return connection;
        }
        if (connectionsPresent >= maxConnections) {
            throw new RuntimeException("Max Connections Utilised");
        }

        connectionsPresent++;
        return createConnection();
    }

    public void releaseConnection(Connection connection) {
        if (connection != null) {
            connectionQueue.offer(connection);
        }
    }
}
