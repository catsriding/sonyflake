package sonyflake.config;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Instant;
import java.util.Enumeration;
import java.util.logging.Logger;
import sonyflake.exception.InvalidMachineIdException;
import sonyflake.exception.NoPrivateAddressException;
import sonyflake.spec.SonyflakeMachineIdSpec;
import sonyflake.spec.SonyflakeStartTimeSpec;

/**
 * Configuration class for Sonyflake ID generation.
 * <p>
 * This class defines the settings used for generating unique IDs, including the start time for ID generation and the
 * machine ID based on the private IP address.
 * </p>
 * <p>
 * The {@code SonyflakeSettings} is immutable and provides a static factory method to create instances with a specified
 * start time.
 * </p>
 */
public final class SonyflakeSettings {

    private static final Logger logger = Logger.getLogger(SonyflakeSettings.class.getName());

    private final Instant startTime;
    private final int machineId;

    /**
     * Creates a new {@code SonyflakeSettings} instance with the specified start time.
     *
     * @param startTime the start time for Sonyflake ID generation, must not be null.
     * @return a new instance of {@code SonyflakeSettings}.
     */
    public static SonyflakeSettings of(Instant startTime) {
        return new SonyflakeSettings(startTime, generateMachineId());
    }

    /**
     * Creates a new {@code SonyflakeSettings} instance with the specified start time and machine ID.
     *
     * @param startTime the start time for Sonyflake ID generation, must not be null.
     * @param machineId the machine ID for Sonyflake ID generation, must be valid according to
     *                  {@link SonyflakeMachineIdSpec}.
     * @return a new instance of {@code SonyflakeSettings}.
     */
    public static SonyflakeSettings of(Instant startTime, int machineId) {
        return new SonyflakeSettings(startTime, machineId);
    }

    /**
     * Constructs a new {@code SonyflakeSettings} with the given start time.
     * <p>
     * This constructor is private to ensure controlled instantiation via the {@code of} static factory method.
     * </p>
     *
     * @param startTime the start time for Sonyflake ID generation.
     */
    private SonyflakeSettings(Instant startTime, int machineId) {
        new SonyflakeStartTimeSpec().check(startTime);
        new SonyflakeMachineIdSpec().check(machineId);

        this.startTime = startTime;
        this.machineId = machineId;
    }

    /**
     * Generates a machine ID based on the private IP address of the machine.
     * <p>
     * The machine ID is computed using the lower 16 bits of the private IP address.
     * </p>
     *
     * @return the machine ID as an integer.
     * @throws InvalidMachineIdException if the machine ID generation fails.
     */
    private static int generateMachineId() {
        try {
            InetAddress ip = getPrivateIp();
            byte[] address = ip.getAddress();
            logger.info("generateMachineId: Private IP address=" + ip.getHostAddress());
            return computeHashLower16Bit(address);
        } catch (Exception e) {
            throw new InvalidMachineIdException("Failed to generate Machine ID.", e);
        }
    }

    /**
     * Computes a hash value using the lower 16 bits of the given IP address.
     *
     * @param address the byte array representing the IP address.
     * @return the computed hash value as an integer.
     */
    private static int computeHashLower16Bit(byte[] address) {
        return ((address[2] & 0xFF) << 8) | (address[3] & 0xFF);
    }

    /**
     * Retrieves the private IP address of the current machine.
     *
     * @return the private IP address as an {@link InetAddress}.
     * @throws NoPrivateAddressException if no private IP address is found.
     */
    private static InetAddress getPrivateIp() throws Exception {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (isPrivateIp(address)) {
                    return address;
                }
            }
        }
        throw new NoPrivateAddressException("No private IP address found.");
    }

    /**
     * Determines whether the given IP address is private.
     *
     * @param address the IP address to check.
     * @return {@code true} if the address is private; {@code false} otherwise.
     */
    private static boolean isPrivateIp(InetAddress address) {
        if (address.isLoopbackAddress() || !(address instanceof java.net.Inet4Address)) return false;

        byte[] ip = address.getAddress();
        int first = ip[0] & 0xFF;
        int second = ip[1] & 0xFF;
        return first == 10 || (first == 172 && second >= 16 && second < 32) || (first == 192 && second == 168);
    }

    /**
     * Retrieves the start time for Sonyflake ID generation.
     *
     * @return the start time as an {@link Instant}.
     */
    public Instant startTime() {
        return startTime;
    }

    /**
     * Retrieves the machine ID for this instance, generated from the private IP address.
     *
     * @return the machine ID as an integer.
     */
    public int machineId() {
        return machineId;
    }
}