package com.stardustindustry.stardustindustry.machine.storage;

/** Grid participation policy for a storage station. */
public enum StorageMode {
    AUTO("auto", true, true),
    CHARGE_ONLY("charge_only", true, false),
    DISCHARGE_ONLY("discharge_only", false, true),
    DISCONNECTED("disconnected", false, false);

    private final String id;
    private final boolean charge;
    private final boolean discharge;

    StorageMode(String id, boolean charge, boolean discharge) {
        this.id = id;
        this.charge = charge;
        this.discharge = discharge;
    }

    public String id() {
        return id;
    }

    public boolean allowsCharge() {
        return charge;
    }

    public boolean allowsDischarge() {
        return discharge;
    }

    public StorageMode next() {
        StorageMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static StorageMode byId(String id) {
        for (StorageMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return AUTO;
    }
}
