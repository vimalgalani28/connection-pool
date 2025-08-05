package com.example.connection_pool.pool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

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

    /**
     * Test: getConnectionWhenConnectionPresentInQueueReturnsConnection
     * Purpose:
     * Ensures that when a connection is present in the queue (idle pool),
     * getConnection() returns it without creating a new one.
     * Expected:
     * - A non-null connection is returned
     * - Only 1 call to DriverManager.getConnection(...) is made
     */
    @Test
    public void getConnectionWhenConnectionPresentInQueueReturnsConnection() {
        // Arrange
        connectionPool = new ConnectionPool("", "", "", 1, 2);

        // Act
        Connection result = connectionPool.getConnection();

        // Assert
        Assertions.assertNotNull(result);
        driverManagerMock.verify(()-> DriverManager.getConnection("", "", ""), times(1));
    }

    /**
     * Test: getConnectionWhenConnectionNotPresentInQueueReturnsNewConnection
     * Purpose:
     * Validates that if no idle connection is available, a new connection is created
     * until the max pool size is reached.
     * Expected:
     * - Second getConnection() creates a new connection
     * - DriverManager.getConnection(...) is called twice
     */
    @Test
    public void getConnectionWhenConnectionNotPresentInQueueReturnsNewConnection() {
        // Arrange
        connectionPool = new ConnectionPool("", "", "", 1, 2);

        // Act
        connectionPool.getConnection();
        Connection result = connectionPool.getConnection();

        // Assert
        Assertions.assertNotNull(result);
        driverManagerMock.verify(()-> DriverManager.getConnection("", "", ""), times(2));
    }

    /**
     * Test: getConnectionWhenConnectionPresentInQueueIsNotValidReturnsNewConnection
     * Purpose:
     * Simulates the case where an idle connection exists but is invalid (closed),
     * so a new connection should be created instead.
     * Expected:
     * - New connection is returned
     * - Total of 2 DriverManager.getConnection() calls: one for invalid, one new
     */
    @Test
    public void getConnectionWhenConnectionPresentInQueueIsNotValidReturnsNewConnection() throws SQLException {
        // Arrange
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isClosed()).thenReturn(true);
        driverManagerMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
            .thenReturn(mockConnection);
        connectionPool = new ConnectionPool("", "", "", 1, 2);

        mockConnection = mock(Connection.class);
        driverManagerMock.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
            .thenReturn(mockConnection);

        // Act
        Connection result = connectionPool.getConnection();

        // Assert
        Assertions.assertNotNull(result);
        driverManagerMock.verify(()-> DriverManager.getConnection("", "", ""), times(2));
    }

    /**
     * Test: getConnectionWhenMaxResourcesAreUtilisedThrowsEx
     * Purpose:
     * Verifies that once max pool size is reached, further getConnection() calls
     * throw an exception instead of blocking or returning null.
     * Expected:
     * - Third call to getConnection() throws RuntimeException
     * - Only 2 DriverManager.getConnection() calls made
     */
    @Test
    public void getConnectionWhenMaxResourcesAreUtilisedThrowsEx() {
        // Arrange
        connectionPool = new ConnectionPool("", "", "", 1, 2);

        // Act
        connectionPool.getConnection();
        connectionPool.getConnection();
        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () -> connectionPool.getConnection());

        // Assert
        Assertions.assertNotNull(ex);
        driverManagerMock.verify(()-> DriverManager.getConnection("", "", ""), times(2));
    }

    /**
     * Test: getConnectionWhenResourcesAreReleasedWhileRetryReturnsConnection
     * Purpose:
     * Ensures that if all connections are in use, and one is released later,
     * a waiting request can acquire that released connection.
     * Expected:
     * - CompletableFuture blocks until releaseConnection() is called
     * - Connection queue is empty afterward
     * - Only 2 DriverManager.getConnection() calls are made
     */
    @Test
    public void getConnectionWhenResourcesAreReleasedWhileRetryReturnsConnection() throws InterruptedException, ExecutionException {
        // Arrange
        connectionPool = new ConnectionPool("", "", "", 1, 2);

        // Act
        connectionPool.getConnection();
        Connection result = connectionPool.getConnection();
        CountDownLatch latch = new CountDownLatch(1);
        CompletableFuture<Connection> connectionFuture = CompletableFuture.supplyAsync(() -> {
            latch.countDown();
            return connectionPool.getConnection();
        });

        latch.await();

        connectionPool.releaseConnection(result);

        Connection result1 = connectionFuture.get();

        // Assert
        Queue<Connection> c = (Queue<Connection>) ReflectionTestUtils.getField(connectionPool, "connectionQueue");
        Assertions.assertEquals(0, c.size());
        Assertions.assertNotNull(result1);
        driverManagerMock.verify(()-> DriverManager.getConnection("", "", ""), times(2));
    }

    /**
     * Test: releaseConnectionAddsBackToQueue
     * Purpose:
     * Verifies that a connection released back to the pool is stored internally and reused on the next request.
     * Expected:
     * - The same connection object is reused (identity check via assertSame).
     * - DriverManager.getConnection(...) is called only once.
     */
    @Test
    public void releaseConnectionAddsBackToQueue() {
        connectionPool = new ConnectionPool("", "", "", 1, 2);

        Connection conn1 = connectionPool.getConnection();
        connectionPool.releaseConnection(conn1);

        Connection conn2 = connectionPool.getConnection();

        Assertions.assertSame(conn1, conn2); // must be same instance
        driverManagerMock.verify(() -> DriverManager.getConnection("", "", ""), times(1));
    }

    /**
     * Test: releaseConnectionWithNullDoesNothing
     * Purpose:
     * Ensures that calling releaseConnection(null) does not throw an exception or corrupt the pool state.
     * Execution:
     * - Call releaseConnection() with null.
     * Expected:
     * - No exception is thrown.
     * - Pool state remains unchanged.
     */
    @Test
    public void releaseConnectionWithNullDoesNothing() {
        connectionPool = new ConnectionPool("", "", "", 1, 2);

        Assertions.assertDoesNotThrow(() -> connectionPool.releaseConnection(null));
    }

    /**
     * Test: releaseConnectionWithSameConnectionTwiceThrowsEx
     * Purpose:
     * Verifies that releasing the same connection twice results in an exception.
     * This ensures that the pool tracks in-use connections and does not allow
     * duplicate releases, which could lead to multiple instances of the same connection
     * in the pool and unsafe concurrent usage.
     * Expected:
     * - RuntimeException is thrown on the second release attempt.
     * - Exception is not null.
     */
    @Test
    public void releaseConnectionWithSameConnectionTwiceThrowsEx() {
        connectionPool = new ConnectionPool("", "", "", 1, 2);
        Connection connection = connectionPool.getConnection();
        connectionPool.releaseConnection(connection);

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () -> connectionPool.releaseConnection(connection));

        Assertions.assertNotNull(ex);
    }

    /**
     * Test: releaseConnectionWithSomeUnknownConnectionThrowsEx
     * Purpose:
     * Verifies that releasing a connection not created or tracked by the pool
     * (i.e., an "unknown" connection) throws an exception.
     * This guards against bugs where external or manually created connections are
     * mistakenly returned to the pool, potentially leading to corruption or
     * incorrect pool behavior.
     * Expected:
     * - RuntimeException is thrown because the connection is not recognized.
     * - Exception is not null.
     */
    @Test
    public void releaseConnectionWithSomeUnknownConnectionThrowsEx() {
        connectionPool = new ConnectionPool("", "", "", 1, 2);
        Connection connection = mock(Connection.class);

        RuntimeException ex = Assertions.assertThrows(RuntimeException.class, () -> connectionPool.releaseConnection(connection));

        Assertions.assertNotNull(ex);
    }

    /**
     * Test: initializePoolWithInvalidInputThrowsEx
     *
     * Purpose:
     * Validates that the ConnectionPool constructor performs input validation and throws
     * an IllegalStateException when provided with invalid or inconsistent input values.
     *
     * Scenarios Tested:
     * - Null URL
     * - Null username
     * - Null password
     * - Null minConnections
     * - Null maxConnections
     * - minConnections > maxConnections (logical inconsistency)
     *
     * Expected:
     * - Each invalid input scenario should throw an IllegalStateException
     * - Constructor should not allow invalid internal state or unbounded behavior
     */
    @Test
    public void initializePoolWithInvalidInputThrowsEx() {
        Assertions.assertThrows(IllegalStateException.class, () -> new ConnectionPool(null, "", "", 1, 2));
        Assertions.assertThrows(IllegalStateException.class, () -> new ConnectionPool("", null, "", 1, 2));
        Assertions.assertThrows(IllegalStateException.class, () -> new ConnectionPool("", "", null, 1, 2));
        Assertions.assertThrows(IllegalStateException.class, () -> new ConnectionPool("", "", "", null, 2));
        Assertions.assertThrows(IllegalStateException.class, () -> new ConnectionPool("", "", "", 1, null));
        Assertions.assertThrows(IllegalStateException.class, () -> new ConnectionPool("", "", "", 2, 1));
    }
} 