package com.stardustindustry.stardustindustry.machine.crusher;

import com.stardustindustry.stardustindustry.StardustIndustry;
import com.stardustindustry.stardustindustry.multiblock.MachinePartTypes;
import com.stardustindustry.stardustindustry.multiblock.model.StructureModel;
import com.stardustindustry.stardustindustry.registry.ModBlocks;

/**
 * The crusher's structure, authored once and shared by the checker and the
 * renderer.
 *
 * <h2>Shape</h2>
 * A 3x3 floor with a 3x3x3 casing body rising above it. The controller is the
 * front-centre cell of the <em>floor</em>, because that is where a player stands
 * to work the machine and because the body above it is strictly projected.
 *
 * <pre>
 *   y=3   casing shell (strict)
 *   y=2   casing shell (strict)
 *   y=1   casing shell (strict)
 *   y=0   base layer: controller + eight free slots
 * </pre>
 *
 * <h2>The base layer</h2>
 * The eight cells around the controller are {@code BASE_SLOT}s: each must hold a
 * port, a filler or an LV base block, and may never be empty. That is where all
 * of the crusher's plumbing lives — item, fluid and energy ports, plus any
 * engineering blocks the player wants — because a static machine never opens a
 * port in its body.
 *
 * <h2>Why the model lives here</h2>
 * The same data drives the server check and the client picture, so the machine
 * can never be built one way and drawn another. The body is redrawn from the
 * ledger (the blocks the player actually placed), so this model only has to
 * describe the shape, not the artwork.
 */
public final class CrusherStructure {

    /** The crusher's footprint: 3x3. */
    public static final int RADIUS = 1;

    /** How many body layers sit above the floor. */
    public static final int BODY_HEIGHT = 3;

    private CrusherStructure() {}

    /**
     * Builds the crusher model: an LV machine whose floor is free and whose body
     * is a fixed cubic shell.
     */
    public static StructureModel build() {
        StructureModel.Builder builder = StructureModel.builder(
                        StardustIndustry.id("crusher"))
                // The controller is the front-centre cell of the floor.
                .controller(0, 0, 0, ModBlocks.CRUSHER.get());

        // Body: a hollow cubic shell, three layers up. Each layer is the 3x3
        // perimeter only — the centre column is left open so the shell is a
        // shell, not a solid block, and the machine has somewhere to work.
        for (int y = 1; y <= BODY_HEIGHT; y++) {
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int z = 0; z <= 2 * RADIUS; z++) {
                    if (x == 0 && z == RADIUS) {
                        continue; // open centre column
                    }
                    builder.fixed(x, y, z, ModBlocks.STEEL_CASING.get());
                }
            }
        }

        // Floor slots: every floor cell except the controller's own.
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = 0; z <= 2 * RADIUS; z++) {
                if (x == 0 && z == 0) {
                    continue; // the controller's cell
                }
                builder.baseSlot(x, 0, z);
            }
        }
        return builder.build();
    }

    /**
     * True when {@code state} may fill a base slot.
     *
     * <p>Kept here so the machine layer can explain a rejection using the same
     * vocabulary the model is authored in.</p>
     */
    public static boolean isBaseSlotContent(net.minecraft.world.level.block.state.BlockState state) {
        return MachinePartTypes.typeOf(state) != null;
    }
}
