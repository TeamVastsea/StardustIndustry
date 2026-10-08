package com.stardustindustry.stardustindustry.client;

import java.util.HashMap;
import java.util.Map;

import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Maps machine block entity types to their client-side animators.
 *
 * <p>Animation is registered here rather than implemented on the block entity,
 * so the machine classes stay free of client code and a dedicated server never
 * loads a render type. Registering an animator for a machine is a single line in
 * client setup; leaving one out is the default, because most machines do not
 * move.</p>
 */
public final class MachineAnimators {

    private static final Map<BlockEntityType<?>, MachineAnimator> ANIMATORS = new HashMap<>();

    private MachineAnimators() {}

    /** Registers the animator for a machine type, replacing any earlier one. */
    public static void register(BlockEntityType<?> type, MachineAnimator animator) {
        ANIMATORS.put(type, animator);
    }

    /** The animator for {@code machine}, or {@code null} when it has none. */
    public static MachineAnimator animatorOf(MachineBlockEntity machine) {
        if (machine.getType() == null) {
            return null;
        }
        return ANIMATORS.get(machine.getType());
    }
}
