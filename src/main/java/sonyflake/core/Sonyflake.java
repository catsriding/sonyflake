package sonyflake.core;

import java.time.Instant;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import sonyflake.config.SonyflakeSettings;
import sonyflake.exception.OverTimeLimitException;
import sonyflake.exception.SonyflakeException;

/**
 * Sonyflake is a distributed unique ID generator inspired by Twitter's Snowflake.
 * <p>
 * This class generates unique IDs using a combination of time, sequence, and machine ID. The ID structure is as
 * follows:
 * </p>
 * <ul>
 *     <li><b>39 bits</b> for time elapsed since the configured start time, in units of 10 milliseconds.</li>
 *     <li><b>8 bits</b> for a sequence number to handle multiple IDs generated within the same time unit.</li>
 *     <li><b>16 bits</b> for a machine ID to ensure uniqueness across nodes in a distributed environment.</li>
 * </ul>
 * <p>
 * The generated IDs are ordered and unique as long as the machine IDs are properly configured.
 * </p>
 */
public final class Sonyflake {

    /**
     * Number of bits allocated for the elapsed time since the start time.
     * <p>
     * This allows for tracking up to {@code 2^39} time units (approximately 17.4 years) if the time unit is 10
     * milliseconds.
     * </p>
     */
    private static final int BIT_LEN_TIME = 39;

    /**
     * Number of bits allocated for the sequence number within the same time unit.
     * <p>
     * This supports up to {@code 2^8 = 256} unique IDs per 10 milliseconds per machine.
     * </p>
     */
    private static final int BIT_LEN_SEQUENCE = 8;

    /**
     * Number of bits allocated for the machine ID.
     * <p>
     * The machine ID ensures uniqueness across nodes in a distributed system. It is derived from the remaining 63 bits
     * after subtracting the time and sequence bits.
     * </p>
     */
    private static final int BIT_LEN_MACHINE_ID = 63 - BIT_LEN_TIME - BIT_LEN_SEQUENCE;

    /**
     * Mask for extracting or resetting the sequence number.
     * <p>
     * This is used to ensure that the sequence number wraps around after reaching its maximum value.
     * </p>
     */
    private static final int SEQUENCE_MASK = (1 << BIT_LEN_SEQUENCE) - 1;

    /**
     * Maximum number of time units (10 milliseconds) that can be represented.
     * <p>
     * This value is {@code 2^39}, which corresponds to approximately 17.4 years.
     * </p>
     */
    private static final long MAX_ELAPSED_TIME = (1L << BIT_LEN_TIME);

    /**
     * Time unit for Sonyflake, represented in nanoseconds.
     * <p>
     * Each unit corresponds to 10 milliseconds ({@code 10_000_000} nanoseconds).
     * </p>
     */
    private static final long SONYFLAKE_TIME_UNIT = 10_000_000L;

    /**
     * The start time from which elapsed time is measured, in Sonyflake time units.
     */
    private final long startTime;

    /**
     * Unique identifier for the machine, derived from its private IP address.
     */
    private final int machineId;

    /**
     * Lock used to ensure thread safety when generating IDs.
     */
    private final Lock mutex = new ReentrantLock();

    /**
     * Elapsed time since the start time, in Sonyflake time units.
     */
    private long elapsedTime = 0;

    /**
     * Sequence number within the current time unit.
     * <p>
     * This starts at the maximum value and wraps around to 0 after reaching the maximum.
     * </p>
     */
    private int sequence = (1 << BIT_LEN_SEQUENCE) - 1;

    /**
     * Creates a new instance of {@code Sonyflake} using the specified settings.
     *
     * @param settings the configuration settings for Sonyflake.
     * @return a new {@code Sonyflake} instance.
     */
    public static Sonyflake of(SonyflakeSettings settings) {
        return new Sonyflake(settings);
    }

    /**
     * Private constructor for {@code Sonyflake}.
     *
     * @param settings the configuration settings for Sonyflake.
     */
    private Sonyflake(SonyflakeSettings settings) {
        this.startTime = toSonyflakeTime(settings.startTime());
        this.machineId = settings.machineId();
    }

    /**
     * Generates the next unique ID.
     *
     * @return the generated unique ID.
     * @throws SonyflakeException if ID generation fails due to time or sequence overflow.
     */
    public long nextId() throws SonyflakeException {
        mutex.lock();
        try {
            checkElapsedTimeExceedsLimit();

            long currentElapsedTime = currentElapsedTime();
            processElapsedTimeExceeded(currentElapsedTime);
            processSequenceOverflow(currentElapsedTime);

            return toId();
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Checks whether the elapsed time exceeds the maximum allowed limit.
     *
     * @throws OverTimeLimitException if the elapsed time exceeds the limit.
     */
    private void checkElapsedTimeExceedsLimit() {
        if (elapsedTime >= MAX_ELAPSED_TIME) {
            throw new OverTimeLimitException("Elapsed time exceeds the maximum limit");
        }
    }

    /**
     * Resets the sequence number when the elapsed time changes.
     *
     * @param currentElapsedTime the current elapsed time.
     */
    private void processElapsedTimeExceeded(long currentElapsedTime) {
        if (this.elapsedTime < currentElapsedTime) {
            this.elapsedTime = currentElapsedTime;
            this.sequence = 0;
        }
    }

    /**
     * Increments the sequence number or waits for the next elapsed time if the sequence overflows.
     *
     * @param currentElapsedTime the current elapsed time.
     */
    private void processSequenceOverflow(long currentElapsedTime) {
        if (elapsedTime != currentElapsedTime) return;

        sequence = (sequence + 1) & SEQUENCE_MASK;
        if (sequence == 0) {
            elapsedTime++;
            waitUntilNextElapsedTime();
        }
    }

    /**
     * Waits until the next elapsed time to generate the next ID.
     */
    private void waitUntilNextElapsedTime() {
        long targetElapsedTime = elapsedTime;
        while (currentElapsedTime() <= targetElapsedTime) {
            long sleepTimeNanos = (targetElapsedTime - currentElapsedTime()) * SONYFLAKE_TIME_UNIT;
            if (sleepTimeNanos > 0) LockSupport.parkNanos(sleepTimeNanos);
        }
    }

    /**
     * Converts the current state to a unique ID.
     *
     * @return the generated unique ID.
     */
    private long toId() {
        return (elapsedTime << (BIT_LEN_SEQUENCE + BIT_LEN_MACHINE_ID))
               | ((long) sequence << BIT_LEN_MACHINE_ID)
               | machineId;
    }

    /**
     * Converts an {@link Instant} to the Sonyflake time format.
     *
     * @param instant the instant to convert.
     * @return the Sonyflake time representation.
     */
    private long toSonyflakeTime(Instant instant) {
        return instant.toEpochMilli() * 1_000_000L / SONYFLAKE_TIME_UNIT;
    }

    /**
     * Retrieves the current elapsed time based on the start time.
     *
     * @return the current elapsed time.
     */
    private long currentElapsedTime() {
        return toSonyflakeTime(Instant.now()) - startTime;
    }

    /**
     * Extracts the elapsed time from a Sonyflake ID.
     *
     * @param id the Sonyflake ID.
     * @return the elapsed time.
     */
    public long elapsedTime(long id) {
        return id >> (BIT_LEN_SEQUENCE + BIT_LEN_MACHINE_ID);
    }

    /**
     * Extracts the sequence number from a Sonyflake ID.
     *
     * @param id the Sonyflake ID.
     * @return the sequence number.
     */
    public int sequenceNumber(long id) {
        return (int) ((id >> BIT_LEN_MACHINE_ID) & SEQUENCE_MASK);
    }

    /**
     * Extracts the machine ID from a Sonyflake ID.
     *
     * @param id the Sonyflake ID.
     * @return the machine ID.
     */
    public int machineId(long id) {
        return (int) (id & ((1 << BIT_LEN_MACHINE_ID) - 1));
    }

    /**
     * Extracts the timestamp from a Sonyflake ID.
     *
     * @param id the Sonyflake ID.
     * @return the timestamp as an {@link Instant}.
     */
    public Instant timestamp(long id) {
        long startEpochMillis = (startTime * SONYFLAKE_TIME_UNIT) / 1_000_000L;
        long elapsedEpochMillis = (elapsedTime(id) * SONYFLAKE_TIME_UNIT) / 1_000_000L;
        return Instant.ofEpochMilli(startEpochMillis).plusMillis(elapsedEpochMillis);
    }
}