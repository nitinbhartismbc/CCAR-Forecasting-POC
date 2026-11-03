**KDB Starter**

**Project Description**

KDB Starter is a reusable Spring Boot starter that simplifies KDB+ integration by providing IPC connectivity, connection pooling, auto-configuration, and centralized query execution capabilities for enterprise applications.

The starter abstracts KDB connection management and enables developers to focus on business logic instead of handling IPC connections, pooling, and infrastructure code.

**Overview**

 KDB Starter provides:

. KDB+ IPC connection management
 
. Connection pooling using Apache Commons Pool2

. Centralized query execution via KdbTemplate

. Externalized configuration through Spring Boot properties

. Built-in health and pool monitoring endpoints

. Reusable integration across multiple Spring Boot applications

. Standardized exception handling

**Features**

    ✅ KDB+ IPC Connectivity
    ✅ Connection Pooling
    ✅ Spring Boot Auto Configuration
    ✅ Centralized Query Execution
    ✅ Health Check Endpoint
    ✅ Connection Pool Metrics Endpoint
    ✅ Custom Exception Handling
    ✅ Lightweight and POC-Friendly Design

**Architecture**

        Application
        |
        v
        KdbTemplate
        |
        v
        KdbConnectionPool
        |
        v
        Apache Commons Pool
        |
        v
        KDB+ IPC Connection
        |
        v
        KDB+ Server

**Prerequisites**

Before using KDB Starter, ensure the following software is available.

**Required Software**

    Java 17+
    Maven 3.8+
    Spring Boot 4.x
    KDB+ Server

**KDB Java API**

The KDB Java API (javakdb-2.2.jar) is required.
Install it into your local Maven repository:

       <dependency>
            <groupId>com.kx</groupId>
            <artifactId>javakdb</artifactId>
            <version>2.2</version>
        </dependency>

**Verify installation:**

~/.m2/repository/com/kx/javakdb/2.2/

**Expected:**

 javakdb-2.2.jar

**Project Structure**

        com.iris.kdb
        │
        ├── config
        │   ├── KdbAutoConfiguration
        │   └── KdbProperties
        │
        ├── controller
        │   └── KdbMetricsController
        │
        ├── pool
        │   ├── KdbConnectionFactory
        │   └── KdbConnectionPool
        │
        ├── service
        │   └── KdbTemplate
        │
        └── exception
            ├── KdbException
            ├── KdbConnectionException
            ├── KdbPoolExhaustedException
            └── KdbQueryException

**Installation**

**Step 1: Build the Starter**

cd backend/kdb-starter
mvn clean install

This installs the starter into your local Maven repository.

**Step 2: Add Dependency**

    <dependency>
        <groupId>com.iris.kdb</groupId>
        <artifactId>kdb-starter</artifactId>
        <version>1.0.0</version>
    </dependency>

**Step 3: Configure Application Properties**

    kdb:
    host: localhost
    port: 5000
    username:
    password:
    
    max-total: 10
    max-idle: 5
    min-idle: 1
    
    max-wait-millis: 5000
    
    test-on-borrow: true
    test-while-idle: true
    
    time-between-eviction-runs-millis: 30000
    min-evictable-idle-time-millis: 60000

**Quick Start**

**Inject KdbTemplate**

        @Service
        @RequiredArgsConstructor
        public class TradeService {
        
            private final KdbTemplate kdbTemplate;
        
            public Long getTradeCount() {
        
                return kdbTemplate.execute(
                        "count trades",
                        Long.class);
            }
        
            public Object getTrades() {
        
                return kdbTemplate.execute(
                        "select from trades");
            }
        }

**Expose REST API**

    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/trades")
    public class TradeController {
    
        private final TradeService tradeService;
    
        @GetMapping("/count")
        public Long getTradeCount() {
            return tradeService.getTradeCount();
        }
    
        @GetMapping
        public Object getTrades() {
            return tradeService.getTrades();
        }
    }

**Auto Configuration**

The starter automatically registers the following Spring Beans:

KdbConnectionPool
KdbTemplate
KdbMetricsController

No additional configuration is required.

**Sample KDB Setup**

**Start KDB:**

q -p 5000

**Create sample table:**

    trades:([] id:1001 1002 1003 1004 1005;
    sym:`AAPL`MSFT`GOOG`TSLA`AMZN;
    qty:100 250 150 75 300)

Verify:

select from trades

**Health Endpoint**

Check KDB connectivity:

  GET /kdb/health

Example Response:

    {
        "connected": true,
        "activeConnections": 0,
        "idleConnections": 1,
        "waitingThreads": 0,
        "maxPoolSize": 10
    }

**Metrics Endpoint**

View pool statistics:

  GET /kdb/metrics

Example Response:

    {
        "host": "localhost",
        "port": 5000,
        "activeConnections": 2,
        "idleConnections": 8,
        "waitingThreads": 0,
        "maxPoolSize": 10
    }

**Exception Handling**

**KdbConnectionException**

Thrown when a connection to KDB cannot be established.

**KdbPoolExhaustedException**

Thrown when all pooled connections are busy and the configured wait time is exceeded.

**KdbQueryException**

Thrown when query execution fails.

**KdbException**

Base exception for all KDB-related exceptions.

**Troubleshooting**

**KDB Connection Failure**

Verify:

    KDB process is running
    Host is reachable
    Port is open
    Credentials are correct

**Dependency Resolution Failure**

Verify:

~/.m2/repository/com/iris/kdb/kdb-starter/1.0.0/

contains:

kdb-starter-1.0.0.jar
kdb-starter-1.0.0.pom

**KDB API Dependency Missing**

Verify:

~/.m2/repository/com/kx/javakdb/2.2/

contains:

 javakdb-2.2.jar

**Benefits**

    Reusable across multiple services
    Standardized KDB access layer
    Reduced boilerplate code
    Centralized connection management
    Improved maintainability
    Consistent exception handling
    Faster onboarding of new applications

**Version**

Current Version: 1.0.0

**Author**
