package com.example.connection_pool;

import com.example.connection_pool.pool.ConnectionPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/connections")
public class TestController {

    @Autowired
    private ConnectionPool connectionPool;

    @GetMapping("/available")
    public ResponseEntity<String> getAvailableConnections() {
        int count = connectionPool.getAvailableConnections();
        return ResponseEntity.ok("Number of connections available: " + count);
    }

    @GetMapping("/idle")
    public ResponseEntity<String> getIdleConnections() {
        int count = connectionPool.getIdleConnections();
        return ResponseEntity.ok("Number of connections idle: " + count);
    }

    @PostMapping("/acquire")
    public ResponseEntity<String> acquireConnection() {
        Connection connection = connectionPool.getConnection();

        // Schedule to release after 5 seconds
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            connectionPool.releaseConnection(connection);
            System.out.println("Connection released after 5 seconds");
        }, 5, TimeUnit.SECONDS);
        return ResponseEntity.ok("Connection Acquired for 5 secs");
    }
}
