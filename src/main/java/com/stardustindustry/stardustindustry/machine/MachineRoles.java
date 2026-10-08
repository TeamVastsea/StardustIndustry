package com.stardustindustry.stardustindustry.machine;

import com.stardustindustry.stardustindustry.multiblock.BlockRole;

/**
 * Which structure roles a machine replaces with an invisible block when it is
 * installed.
 *
 * <p>Only the strict body is hidden. The base layer is the floor the player
 * stands on and where every port lives, and the design keeps it visible: it is
 * the machine's "foundation", and hiding it would leave the machine floating.
 * For a static machine the body is exactly the fixed cells, which the evaluator
 * reports as {@link com.stardustindustry.stardustindustry.multiblock.BlockRole#FRAME}.</p>
 *
 * <p>Ports and the controller are excluded for the same reason they are not body:
 * a port keeps its block entity so automation can still reach the machine, and
 * the controller owns the renderer.</p>
 *
 * <p>Dynamic machines never install — they form on their own and keep their shell
 * in the world — so this rule never applies to them.</p>
 */
public final class MachineRoles {

    private MachineRoles() {}

    /** True when a cell with this role is hidden on install and redrawn on the client. */
    public static boolean isHidable(BlockRole role) {
        return isHidable(role.name());
    }

    /** True when a ledger entry with this role name is hidden and redrawn. */
    public static boolean isHidable(String roleName) {
        // Only the strict body. BASE and FILLER sit on the base layer, which
        // stays visible, and PORT and CONTROLLER were never hidden in the first
        // place.
        return "FRAME".equals(roleName);
    }
}
