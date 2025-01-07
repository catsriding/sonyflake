package sonyflake.spec;

import sonyflake.exception.InvalidMachineIdException;

public class SonyflakeMachineIdSpec {

    private static final int MAX_MACHINE_ID = (1 << 16) - 1;

    /**
     * Validates the provided SonyflakeSettings.
     *
     * @param settings the SonyflakeSettings to validate.
     */
    public void check(int machineId) {
        validateMachineId(machineId);
    }

    private void validateMachineId(int machineId) {
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new InvalidMachineIdException("MachineId is out of range: " + machineId);
        }
    }
}
