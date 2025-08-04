package com.example.connection_pool.pool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ConnectionPoolTest {

    private ConnectionPool connectionPool;

    private MockedStatic<DriverManager> driverManagerMock;

    @BeforeEach
    void setUp() {
        Connection mockConnection = mock(Connection.class);
        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
            .thenReturn(mockConnection);
    }

    @AfterEach
    void tearDown() {
        driverManagerMock.close();
    }

    @Test
    public void getConnectionWhenConnectionPresentInQueueReturnsConnection() {
        connectionPool = new ConnectionPool("", "", "", 1, 2);
        Connection result = connectionPool.getConnection();
        Assertions.assertNotNull(result);
        driverManagerMock.verify(()-> DriverManager.getConnection("", "", ""), times(1));
    }

    @Test
    public void getConnectionWhenConnectionNotPresentInQueueReturnsNewConnection() {
        connectionPool = new ConnectionPool("", "", "", 1, 2);
        connectionPool.getConnection();
        Connection result = connectionPool.getConnection();
        Assertions.assertNotNull(result);
        driverManagerMock.verify(()-> DriverManager.getConnection("", "", ""), times(2));
    }

    @Test
    public void getConnectionWhenMaxResourcesAreUtilisedThrowsEx() {
        connectionPool = new ConnectionPool("", "", "", 1, 2);
        connectionPool.getConnection();
        connectionPool.getConnection();
        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () -> connectionPool.getConnection());
        Assertions.assertNotNull(ex);
        driverManagerMock.verify(()-> DriverManager.getConnection("", "", ""), times(2));
    }
} 