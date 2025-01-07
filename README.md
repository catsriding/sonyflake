# 🌟 Sonyflake - Java ☕

Sonyflake is a distributed unique ID generator inspired by Twitter's Snowflake.
This Java implementation is based on the
original [Go implementation by Sony](https://github.com/sony/sonyflake/tree/master).
It ensures efficient, unique ID generation in both single-node and distributed environments.

## 🧩 ID Structure

Sonyflake IDs are composed of three parts:

| **Component**     | **Bits** | **Description**                                                             |
|-------------------|----------|-----------------------------------------------------------------------------|
| ⏰ **Time**        | 39       | Represents elapsed time in 10ms units since the `startTime`.                |
| 🔢 **Sequence**   | 8        | A counter that increments within the same 10ms window.                      |
| 🖥 **Machine ID** | 16       | A unique identifier for the machine, typically derived from its private IP. |

**Total: 63 bits 🧮**

## 📂 Project Structure

```shell
.
├── .git
├── .gitignore
├── .gradle
├── .idea
├── build.gradle
├── gradle
├── gradlew
├── gradlew.bat
├── README.md
├── settings.gradle
└── src
    ├── main
    │   ├── java
    │   │   └── sonyflake
    │   │       ├── config
    │   │       │   └── SonyflakeSettings.java
    │   │       ├── core
    │   │       │   └── Sonyflake.java
    │   │       ├── exception
    │   │       │   ├── InvalidMachineIdException.java
    │   │       │   ├── InvalidStartTimeException.java
    │   │       │   ├── NoPrivateAddressException.java
    │   │       │   ├── OverTimeLimitException.java
    │   │       │   └── SonyflakeException.java
    │   │       └── spec
    │   │           ├── SonyflakeMachineIdSpec.java
    │   │           └── SonyflakeStartTimeSpec.java
    │   └── resources
    └── test
        ├── java
        │   └── sonyflake
        │       └── core
        │           └── SonyflakeTest.java
        └── resources
```

### 🏗 Core Class

- `Sonyflake`: The main class responsible for generating unique IDs.
  - Elapsed Time: The time elapsed (in 10ms units) since the configured startTime.
  - Sequence Number: Ensures uniqueness within the same 10ms window.
  - Machine ID: A unique identifier for each machine to prevent collisions in distributed systems.
- `SonyflakeSettings`: Configuration class for Sonyflake.
  - `startTime`: The base epoch from which elapsed time is calculated.
  - `machineId`: (Optional) The unique ID of the machine. Defaults to being auto-generated from the machine's IP
    address.

### ⚡ Key Methods

- `Sonyflake.of()`: Factory method to initialize the `Sonyflake` instance with the provided settings.
- `SonyflakeSettings.of()`: Factory method to initialize the `SonyflakeSettings` instance with the provided time and
  machine ID.
- `sonyflake.nextId()`: Generates the next unique ID. This method is thread-safe and supports high-throughput
  environments.

## 🛠 How It Works

The **Java implementation of Sonyflake** generates distributed unique IDs efficiently. Below are the key components:

### 1. Initialization

`SonyflakeSettings` configures the generator. You can define:

- **`startTime`**: The base epoch from which IDs are calculated.
- **`machineId`**: A unique identifier for each machine, ensuring collision-free IDs in distributed environments.

By default, the `machineId` is derived automatically from the machine's IP address.

#### Default Configuration

```java
SonyflakeSettings settings = SonyflakeSettings.of(Instant.now());
```

#### Custom Configuration

```java
SonyflakeSettings settings = SonyflakeSettings.of(Instant.now(), 42); // Custom machineId
```

### 2. ID Generation

Once configured, you can generate unique IDs as shown below:

```java
SonyflakeSettings settings = SonyflakeSettings.of(Instant.now());
Sonyflake sonyflake = Sonyflake.of(settings);
long id = sonyflake.nextId();
```

Each generated ID combines:

- **Time**: Ensures chronological ordering.
- **Sequence**: Maintains uniqueness within the same time unit.
- **Machine ID**: Prevents collisions across distributed instances.

## 🚀 Performance Testing

This implementation has been tested for high-throughput environments. Below is a simulation of 50,000 TPS using
multithreaded instances:

### 🧪 Simulate High TPS

```java

@Test
void shouldHandleHighTpsWithMultipleInstances() throws Exception {

    // Given
    int totalRequests = 50_000; // 50,000 TPS
    int threadCount = 10;       // 10 machines
    int expected = 1_000; // expected elapsed time in millis
    AtomicInteger idCount = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(totalRequests);

    List<Sonyflake> sonyflakeInstances = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
        SonyflakeSettings settings = SonyflakeSettings.of(Instant.parse("2025-01-01T00:00:00Z"), i + 1);
        sonyflakeInstances.add(Sonyflake.of(settings));
    }

    // When
    long startTime = System.nanoTime();
    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
        for (int i = 0; i < totalRequests; i++) {
            int threadIndex = i % threadCount;
            executor.submit(() -> {
                try {
                    sonyflakeInstances.get(threadIndex).nextId();
                    idCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
    }
    long endTime = System.nanoTime();
    long actual = (endTime - startTime) / 1_000_000;

    // Then
    assertTrue(actual < expected, "Expected: less than" + expected + ", Actual: " + actual + " ms");
    assertEquals(totalRequests, idCount.get(), "Expected: " + totalRequests + ", Actual: " + idCount.get());
}
```

## 📝 Sample Generation

```
Generated ID: 928427846729870
Elapsed Time: 55338612
Sequence Number: 1
Machine Id: 142
Timestamp: 2025-01-07T09:43:06.120Z
```

## 📌 Key Considerations

- **Time Dependency**: Ensure all instances use a consistent `startTime`.
- **Machine ID Uniqueness**: Assign unique machine IDs in distributed setups to avoid collisions.
- **Max Throughput**: Each instance supports up to **25,600 TPS** (256 IDs per 10ms).

