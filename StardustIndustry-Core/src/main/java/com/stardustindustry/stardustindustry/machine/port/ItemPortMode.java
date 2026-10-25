package com.stardustindustry.stardustindustry.machine.port;

/** Server-authoritative automation direction for an item port. */
public enum ItemPortMode {
    BOTH("both", true, true),
    INPUT("input", true, false),
    OUTPUT("output", false, true),
    DISABLED("disabled", false, false);

    private final String id;
    private final boolean insert;
    private final boolean extract;

    ItemPortMode(String id, boolean insert, boolean extract) {
        this.id = id;
        this.insert = insert;
        this.extract = extract;
    }

    public String id() {
        return id;
    }

    public boolean allowsInsert() {
        return insert;
    }

    public boolean allowsExtract() {
        return extract;
    }

    public ItemPortMode next() {
        ItemPortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static ItemPortMode byId(String id) {
        for (ItemPortMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return BOTH;
    }
}
