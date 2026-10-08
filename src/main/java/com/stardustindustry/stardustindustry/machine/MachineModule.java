package com.stardustindustry.stardustindustry.machine;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * A pluggable capability of a machine.
 *
 * <p>A machine is not defined by its class hierarchy but by the set of modules
 * it owns: an energy buffer, an item inventory, a fluid tank, a recipe runner,
 * an upgrade rack and so on. Each module is a small, self-contained unit that
 * owns its own state, ticks itself, saves itself and exposes whatever NeoForge
 * capability it represents. Composing modules is what keeps one {@code
 * MachineBlockEntity} able to serve a crusher, a boiler and a reactor without a
 * class per machine.</p>
 *
 * <p>Modules are created by the machine in {@link #addModule} and are then
 * driven by the machine: {@link #serverTick()} while the machine is loaded,
 * {@link #save}/{@link #load} around chunk saves, and {@link #onStructureChanged}
 * when the surrounding multiblock forms or breaks.</p>
 */
public interface MachineModule {

    /**
     * Called once when the module is attached, before any other callback. Use
     * this to capture a reference to the owning machine.
     */
    default void attach(MachineBlockEntity machine) {}

    /**
     * Called every server tick while the machine is loaded and chunk-loaded.
     * Modules that do nothing per tick simply leave this empty.
     */
    default void serverTick() {}

    /**
     * Called every client tick. Modules that only exist for rendering or sound
     * override this; the default is a no-op so most modules stay server-only.
     */
    default void clientTick() {}

    /**
     * Called after the machine's structure transitions between formed and
     * unformed. Modules that only operate inside a complete structure (a
     * reactor core, a boiler drum) use this to start and stop.
     *
     * @param formed true when the structure just completed
     */
    default void onStructureChanged(boolean formed) {}

    /**
     * Writes this module's persistent state. The machine provides the tag and
     * the registry lookup; modules must write under their own child tag so
     * several modules can coexist without collisions.
     */
    default void save(CompoundTag tag, HolderLookup.Provider registries) {}

    /** Reads state written by {@link #save}. */
    default void load(CompoundTag tag, HolderLookup.Provider registries) {}

    /**
     * A stable name used as the NBT child key. Defaults to the simple class
     * name lower-cased; modules whose behaviour changes over time should
     * override it to keep saves stable.
     */
    default String storageKey() {
        String simple = getClass().getSimpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }
}
