package com.stardustindustry.stardustindustry.machine.tank;

/**
 * What a tank stores and, with it, the two things a tank's medium decides: the
 * shell block that forms its walls and the word used to describe its contents.
 *
 * <p>A fluid tank and a gas tank are the same vessel with the same geometry, the
 * same ports-per-wall rule and the same capacity arithmetic. Everything about
 * them that differs follows from this enum — which block counts as their shell,
 * whether their buffer speaks {@code IFluidHandler} or {@code IGasHandler}, and
 * how their contents are drawn — so the shared tank code branches here rather
 * than being written twice.</p>
 *
 * <h2>Why not two fully separate tanks</h2>
 * Duplicating the structure scan, the anchor rule, the sync and the parameter
 * screen for gases would mean every tank bug had to be fixed twice. Keeping one
 * implementation with a medium switch means a fluid fix is a gas fix.</p>
 */
public enum TankMedium {
    /** A tank of liquid: holds one fluid, rendered by level. */
    FLUID("fluid", "fluid_tank_shell"),
    /** A tank of gas: holds one gas, rendered by concentration. */
    GAS("gas", "gas_tank_shell");

    private final String id;
    private final String shellBlockName;

    TankMedium(String id, String shellBlockName) {
        this.id = id;
        this.shellBlockName = shellBlockName;
    }

    /** The lower-case id used in structure names, translation keys and NBT. */
    public String id() {
        return id;
    }

    /** The registry path of this medium's shell block. */
    public String shellBlockName() {
        return shellBlockName;
    }

    /** The other medium; the two are a closed pair. */
    public TankMedium other() {
        return this == FLUID ? GAS : FLUID;
    }

    /** The medium for an id, or {@code null} when it is neither. */
    public static TankMedium byId(String id) {
        for (TankMedium medium : values()) {
            if (medium.id.equals(id)) {
                return medium;
            }
        }
        return null;
    }
}
