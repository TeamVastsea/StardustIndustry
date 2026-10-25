package com.stardustindustry.stardustindustry.menu;

import com.stardustindustry.stardustindustry.energy.CableConnection;
import com.stardustindustry.stardustindustry.energy.CableRunId;
import com.stardustindustry.stardustindustry.energy.ElectricalNetworkSavedData;
import com.stardustindustry.stardustindustry.energy.ElectricalTelemetrySnapshot;
import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;
import com.stardustindustry.stardustindustry.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** Read-only menu opened by the electrician's meter. */
public final class ElectricalDiagnosticMenu extends TelemetryMenu {
    private final CableRunId cableRun;

    public ElectricalDiagnosticMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, ElectricalTelemetrySnapshot.STREAM_CODEC.decode(data), decodeCableRun(data));
    }

    public ElectricalDiagnosticMenu(int id, Inventory inventory, ElectricalTelemetrySnapshot snapshot) {
        this(id, inventory, snapshot, null);
    }

    public ElectricalDiagnosticMenu(int id, Inventory inventory, ElectricalTelemetrySnapshot snapshot,
                                    CableRunId cableRun) {
        super(ModMenus.ELECTRICAL_DIAGNOSTIC.get(), id, inventory, snapshot);
        this.cableRun = cableRun;
    }

    @Override
    protected ElectricalTelemetrySnapshot captureSnapshot(ServerLevel level) {
        return cableRun == null
                ? super.captureSnapshot(level)
                : ElectricalTelemetrySnapshot.captureCable(level, targetPos(), cableRun);
    }

    @Override
    protected boolean isExpectedTarget(Player player, BlockPos pos) {
        if (cableRun == null) {
            return player.level().getBlockEntity(pos) != null;
        }
        if (!(player.level().getBlockEntity(pos) instanceof TerminalMountBlockEntity)
                || !cableRun.touches(pos)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        CableConnection cable = ElectricalNetworkSavedData.get(level).cable(cableRun);
        return cable != null && cable.runId().equals(cableRun);
    }

    private static CableRunId decodeCableRun(RegistryFriendlyByteBuf data) {
        return data.readBoolean() ? CableRunId.STREAM_CODEC.decode(data) : null;
    }
}
