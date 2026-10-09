package com.stardustindustry.stardustindustry.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.stardustindustry.stardustindustry.capability.ResourceStack;
import com.stardustindustry.stardustindustry.capability.ResourceType;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * The ordered collection of modules owned by one machine.
 *
 * <p>Modules are attached at construction, then driven in registration order.
 * A module can be looked up by its concrete class, which is how other code
 * reaches a specific capability (for example, a recipe runner asking the
 * machine for its energy module).</p>
 *
 * <p>The collection also acts as the machine's resource router: given a
 * {@link ResourceType}, it reports whether any module provides that capability,
 * so the recipe engine can reject a recipe up front instead of failing halfway
 * through.</p>
 */
public final class ModuleHost {

    private final MachineBlockEntity machine;
    private final List<MachineModule> modules = new ArrayList<>();
    private final Map<ResourceType, MachineModule> providers = new EnumMap<>(ResourceType.class);

    public ModuleHost(MachineBlockEntity machine) {
        this.machine = machine;
    }

    /** Attaches a module and returns it, so construction can chain. */
    public <T extends MachineModule> T add(T module) {
        module.attach(machine);
        modules.add(module);
        return module;
    }

    /** Registers a module as the provider of a resource capability. */
    public <T extends MachineModule> T provide(ResourceType type, T module) {
        providers.put(type, module);
        return module;
    }

    /** The first module of the given class, or {@code null} if none is attached. */
    @SuppressWarnings("unchecked")
    public <T extends MachineModule> T get(Class<T> type) {
        for (MachineModule module : modules) {
            if (type.isInstance(module)) {
                return (T) module;
            }
        }
        return null;
    }

    /** True when some module can supply the given resource type. */
    public boolean provides(ResourceType type) {
        return providers.containsKey(type);
    }

    /** The module owning the given resource type, or {@code null}. */
    public MachineModule provider(ResourceType type) {
        return providers.get(type);
    }

    /** An unmodifiable view for iteration by the machine. */
    public List<MachineModule> all() {
        return Collections.unmodifiableList(modules);
    }

    public void serverTick() {
        for (MachineModule module : modules) {
            module.serverTick();
        }
    }

    public void clientTick() {
        for (MachineModule module : modules) {
            module.clientTick();
        }
    }

    public void onStructureChanged(boolean formed) {
        for (MachineModule module : modules) {
            module.onStructureChanged(formed);
        }
    }

    /** Saves every module under its own storage key. */
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        for (MachineModule module : modules) {
            CompoundTag child = new CompoundTag();
            module.save(child, registries);
            tag.put(module.storageKey(), child);
        }
    }

    /** Loads every module from its storage key, tolerating absent modules. */
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        for (MachineModule module : modules) {
            if (tag.contains(module.storageKey())) {
                module.load(tag.getCompound(module.storageKey()), registries);
            }
        }
    }

    /** Total resource amount currently buffered, for diagnostics and GUIs. */
    public int buffered(ResourceType type) {
        MachineModule module = providers.get(type);
        if (module instanceof ResourceHolder holder) {
            return holder.buffered();
        }
        return 0;
    }

    /**
     * Implemented by modules that hold a measurable amount of a resource so the
     * collection can report a single number without knowing the module's shape.
     */
    public interface ResourceHolder {
        int buffered();
    }
}
