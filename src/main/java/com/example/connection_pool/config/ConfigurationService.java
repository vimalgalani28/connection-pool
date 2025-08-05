package com.example.connection_pool.config;

import com.example.connection_pool.pool.ConnectionPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigurationService {


    @Value("${DB_USERNAME}")
    private String userName;
    @Value("${DB_PASSWORD}")
    private String password;

    @Bean
    public ConnectionPool connectionPool() {
        return new ConnectionPool(
            "jdbc:mysql://mysql:3306/testdb", // use Docker service name
            userName,
            password,
            1,
            3
        );
    }
}
