# Connection Pool Project

A Java Spring Boot application demonstrating a custom database connection pool implementation.

## Running Steps

### 1. Environment Setup
Create a `.env` file in the root directory:
```env
DB_USERNAME=root
DB_PASSWORD=root
```

### 2. Running Tests
```bash
mvn clean install
```

### 3. Running Application
```bash
docker-compose up --build
```

## Connection Pool Features

### Core Features
- **Connection Pooling**: Maintains a pool of database connections for reuse
- **Configurable Pool Size**: Supports minimum and maximum connection limits
- **Thread-Safe Access**: Provides thread-safe connection retrieval and release
- **Connection Reuse**: Efficiently reuses database connections to improve performance
- **Error Handling**: Robust error handling for connection failures with proper exception propagation

### Pool Behavior
- **Initialization**: Creates minimum number of connections on startup
- **Connection Retrieval**: Returns available connections from pool or creates new ones when needed
- **Connection Release**: Returns connections back to the pool for reuse
- **Max Limit Enforcement**: Prevents exceeding maximum connection count
- **FIFO Queue**: Uses LinkedList for first-in-first-out connection distribution
- **State Tracking**: Maintains accurate count of active connections

### Configuration Options
- **URL**: Database connection URL
- **Username**: Database username
- **Password**: Database password
- **Min Connections**: Initial pool size
- **Max Connections**: Maximum allowed connections

## Project Structure

```
connection_pool/
├── src/main/java/com/example/connection_pool/
│   ├── pool/ConnectionPool.java          # Main connection pool implementation
│   ├── config/ConfigurationService.java  # Configuration management
│   ├── TestController.java               # REST API endpoints
│   └── ConnectionPoolApplication.java    # Spring Boot application
├── src/test/java/com/example/connection_pool/pool/
│   ├── ConnectionPoolTest.java           # Unit tests
│   └── ConnectionPoolTestRunner.java     # Test runner
├── docker-compose.yml                    # Docker services configuration
├── Dockerfile                           # Application container
└── pom.xml                             # Maven dependencies
``` 