package com.stardustindustry.stardustindustry.machine.tank;

import java.util.Map;

import com.stardustindustry.stardustindustry.machine.MachineBlockEntity;
import com.stardustindustry.stardustindustry.machine.MachineParamsData;
import com.stardustindustry.stardustindustry.machine.MachineTier;
import com.stardustindustry.stardustindustry.machine.MachinePortBlock;
import com.stardustindustry.stardustindustry.multiblock.BlockRole;
import com.stardustindustry.stardustindustry.multiblock.provider.StructureEvaluation;
import com.stardustindustry.stardustindustry.multiblock.provider.StructureProvider;
import com.stardustindustry.stardustindustry.multiblock.provider.TankStructureProvider;
import com.stardustindustry.stardustindustry.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shared body of every tank: a hollow rectangular vessel whose capacity is
 * the volume it encloses.
 *
 * <p>A fluid tank and a gas tank differ only in their medium — one holds a
 * {@code FluidStack}, the other a {@code GasStack}, and one draws by level while
 * the other draws by concentration. Everything else is identical: the closed-box
 * shape and its edge/face rules, the deterministic anchor, the dynamic capacity
 * read from the interior, the box the client is told about, the formation
 * announcement, and the parameter screen. All of that lives here once, and each
 * concrete tank supplies its medium, its block-entity type and its contents.</p>
 *
 * <h2>No tier</h2>
 * A tank has no voltage tier. It is a vessel, not a machine: its frame and shell
 * are inert, its capacity comes from the volume it encloses, and the only thing
 * its ports change is how fast its medium moves in and out.</p>
 *
 * <h2>Formation</h2>
 * A tank forms on its own, with no installation step: the shell is a closed box
 * the player keeps editing, and a dynamic machine that locked itself would fight
 * that. Because the player gets no confirmation from a button press, a newly
 * formed tank announces itself to nearby players.</p>
 *
 * <h2>Medium and structure</h2>
 * A tank's medium is fixed by the shell block that owns it; {@link TankStructureProvider}
 * accepts only panels of the same medium, so a fluid shell cannot be part of a
 * gas tank and vice versa. The shared frame and industrial glass belong to both.
 */
public abstract class AbstractTankBlockEntity extends MachineBlockEntity {

    /** The box corner last synced to the client, for the level renderer and the HUD. */
    private BlockPos syncedMin;
    /** The opposite box corner last synced to the client. */
    private BlockPos syncedMax;
    /** The capacity last synced to the client, in mB. */
    private int syncedCapacityMb;

    protected AbstractTankBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                      BlockPos pos, BlockState state) {
        // The tank is not electric, so the machine tier is nominal; the tank
        // provider never reports a tier and the module set has no energy buffer.
        //
        // Modules are attached by the concrete subclass, after it has created its
        // buffer field: createModules() runs from initialiseModules() and must be
        // able to see that field, which is not yet assigned during this
        // constructor.
        super(type, pos, state, MachineTier.LV);
    }

    /** The medium this tank stores; decides its shell block and its buffer kind. */
    public abstract TankMedium medium();

    /** The amount currently held, in mB. */
    public abstract int storedAmountMb();

    /**
     * The interactive-tooltip snapshot of this tank's contents, or {@code null}
     * when it is empty. Used by the highlight-mod integrations, which format the
     * name themselves.
     */
    public abstract TankContents contents();

    @Override
    public StructureProvider provider() {
        return TankStructureProvider.INSTANCE;
    }

    /**
     * A tank draws no projection ghosts.
     *
     * <p>Its shape is whatever box the player is building, so there is no single
     * block a ghost could correctly represent; the floating text still reports
     * the first thing that is wrong.</p>
     */
    @Override
    public boolean supportsProjection() {
        return false;
    }

    /**
     * The tank's capacity in mB.
     *
     * <p>On the server this is derived live from the current structure
     * evaluation, so resizing the tank takes effect immediately. On the client
     * there is no evaluation (the server owns it), so the capacity the server
     * shipped in the update tag is used instead.</p>
     */
    public int currentCapacityMb() {
        StructureEvaluation evaluation = evaluation();
        if (evaluation != null && evaluation.formed()) {
            return TankStructureProvider.capacityMb(evaluation);
        }
        return level != null && level.isClientSide() ? syncedCapacityMb : 0;
    }

    /** The box corner the client last received, or {@code null} when none has arrived. */
    public BlockPos syncedMin() {
        return syncedMin;
    }

    /** The opposite box corner the client last received, or {@code null}. */
    public BlockPos syncedMax() {
        return syncedMax;
    }

    /** The capacity last shipped to the client, in mB, for the renderer's diagnostics. */
    public int syncedCapacityMb() {
        return syncedCapacityMb;
    }

    /**
     * Announces a newly formed tank, since the player built it without pressing
     * anything and otherwise gets no confirmation that it worked.
     */
    @Override
    protected void onFormedChanged(boolean nowFormed) {
        if (!nowFormed) {
            return;
        }
        int[] size = evaluatedSize();
        if (size.length != 3) {
            return;
        }
        int volume = Math.max(0, size[0] - 2) * Math.max(0, size[1] - 2) * Math.max(0, size[2] - 2);
        int capacity = volume * com.stardustindustry.stardustindustry.Config.TANK_BUCKETS_PER_AIR_BLOCK.get() * 1000;
        broadcastNearby(Component.translatable(
                "message.stardustindustry.tank.formed", size[0], size[1], size[2], capacity), 24.0);
    }

    // ---- client sync ----

    @Override
    protected void saveMachine(CompoundTag tag, HolderLookup.Provider registries) {
        StructureEvaluation evaluation = evaluation();
        if (evaluation != null && evaluation.formed() && !evaluation.roles().isEmpty()) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : evaluation.roles().keySet()) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            tag.putIntArray("TankBox", new int[] {minX, minY, minZ, maxX, maxY, maxZ});
            tag.putInt("TankCapacity", TankStructureProvider.capacityMb(evaluation));
        }
    }

    @Override
    protected void loadMachine(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("TankBox")) {
            int[] box = tag.getIntArray("TankBox");
            if (box.length == 6) {
                syncedMin = new BlockPos(box[0], box[1], box[2]);
                syncedMax = new BlockPos(box[3], box[4], box[5]);
            }
        }
        syncedCapacityMb = tag.getInt("TankCapacity");
    }

    /**
     * Describes the tank for its parameter screen: what it is, how big it is,
     * what is inside it, and which ports are bolted to it.
     *
     * <p>The ports are counted from the last structure evaluation rather than
     * from a stored list, so re-scanning the structure is also what refreshes
     * the screen. Each port's tier is read back off its block, which is the same
     * value the port's throughput is derived from.</p>
     */
    @Override
    protected MachineParamsData.TankParams tankParams() {
        StructureEvaluation evaluation = evaluation();
        int sizeX = 0, sizeY = 0, sizeZ = 0;
        Map<String, Integer> portCounts = new java.util.LinkedHashMap<>();
        String maxRateTier = "";
        int maxRate = 0;

        if (evaluation != null && evaluation.formed() && level != null) {
            int[] size = evaluatedSize();
            if (size.length == 3) {
                sizeX = size[0];
                sizeY = size[1];
                sizeZ = size[2];
            }
            for (var entry : evaluation.roles().entrySet()) {
                if (entry.getValue() != BlockRole.PORT) {
                    continue;
                }
                var portBlock = level.getBlockState(entry.getKey()).getBlock();
                if (!(portBlock instanceof MachinePortBlock port) || port.tier() == null) {
                    continue;
                }
                String tier = port.tier().getSerializedName();
                portCounts.merge(tier, 1, Integer::sum);
                if (port.tier().fluidTransfer() > maxRate) {
                    maxRate = port.tier().fluidTransfer();
                    maxRateTier = tier;
                }
            }
        }

        StringBuilder ports = new StringBuilder();
        portCounts.forEach((tier, count) -> {
            if (!ports.isEmpty()) {
                ports.append(',');
            }
            ports.append(tier).append(':').append(count);
        });

        String contentsKey = "";
        String contentsNameEn = "";
        TankContents contents = contents();
        if (contents != null) {
            contentsKey = contents.translationKey();
            contentsNameEn = contents.englishName();
        }

        return new MachineParamsData.TankParams(medium().id(), sizeX, sizeY, sizeZ,
                contentsKey, contentsNameEn, ports.toString(), maxRateTier, maxRate);
    }

    /**
     * A tank's contents, in the medium-neutral form the HUD needs.
     *
     * <p>The name is carried as a pair so every caller can show it the same way:
     * a resolved component for the current language, plus the English name for
     * the case where the server resolved it.</p>
     *
     * @param translationKey the contents' translation key
     * @param englishName    the contents' English name
     * @param amountMb       the amount held, in mB
     */
    public record TankContents(String translationKey, String englishName, int amountMb) {}
}
