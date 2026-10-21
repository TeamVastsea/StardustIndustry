package com.stardustindustry.stardustindustry.energy;

import java.util.ArrayList;
import java.util.List;

import com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Creates and removes cables across both racks they join.
 *
 * <h2>Why this is a separate class</h2>
 * <p>A cable is stored twice — half on each rack — and the two halves must always
 * agree. Writing the two ends inline at each call site makes it easy to update one
 * and forget the other, which shows up later as a cable hanging from a terminal
 * that no longer exists. Every mutation goes through here so there is exactly one
 * place that knows the rule.</p>
 *
 * <h2>Cables do not restrict which terminals they join</h2>
 * <p>There is no tier check and no per-terminal limit. A cable has no voltage
 * level of its own; it has a current class and an insulation class, and the
 * insulation only decides what a player <b>feels</b> when touching it
 * ({@code CableInsulation#shockAt}). A player may therefore string a thin,
 * LV-sheathed cable onto a 10 kV grid — it will carry the current and shock
 * whoever touches it, which is the lesson §6.10–§6.11 wants to teach. Refusing
 * the connection would hide that.</p>
 *
 * <h2>Server only</h2>
 * <p>These methods mutate block entities and must run on the server thread.</p>
 */
public final class CableService {

    private CableService() {}

    /**
     * Strings a cable between two terminals.
     *
     * <p>Fails and changes nothing unless both endpoints are loaded racks, both
     * cells hold a terminal, and the two ends are not the same terminal. The caller
     * is expected to have checked what it wants to report; this returns
     * {@code false} rather than throwing so a stale client request is a no-op.</p>
     */
    public static boolean connect(Level level, TerminalEndpoint from, TerminalEndpoint to,
                                  CableAmpacity ampacity, CableInsulation insulation) {
        return connect(level, from, to, ampacity, insulation, CableColor.UNDYED);
    }

    /**
     * The colour a cable starts life in when nobody dyed it: <b>unpainted</b>.
     *
     * <p>Not a fixed colour, and deliberately so. What an unpainted cable looks
     * like is decided when it is drawn ({@link CableColor#naturalColor}): bare
     * conductor shows its metal, insulated cable shows its grey sheath. Storing
     * "unpainted" rather than baking a grey or a metal tone into the record means a
     * cable's appearance can never contradict its product — a bare wire stays
     * copper even if an old save wrote grey into it.</p>
     */
    public static CableColor defaultColorOf(CableAmpacity ampacity) {
        return CableColor.UNDYED;
    }

    /** As {@link #connect(Level, TerminalEndpoint, TerminalEndpoint, CableAmpacity, CableInsulation)}, in a colour. */
    public static boolean connect(Level level, TerminalEndpoint from, TerminalEndpoint to,
                                  CableAmpacity ampacity, CableInsulation insulation, CableColor color) {
        if (level.isClientSide() || from.equals(to)) {
            return false;
        }
        // Steel-cored aluminium has no insulated product; refuse it here too, so a
        // hand-made or forged request cannot conjure a cable that cannot be made.
        if (ampacity.isBareOnly() && insulation != CableInsulation.BARE) {
            return false;
        }
        TerminalMountBlockEntity fromRack = rackAt(level, from.pos());
        TerminalMountBlockEntity toRack = rackAt(level, to.pos());
        if (fromRack == null || toRack == null) {
            return false;
        }
        if (!fromRack.hasTerminal(from.cell()) || !toRack.hasTerminal(to.cell())) {
            return false;
        }
        CableConnection cable = new CableConnection(ampacity, insulation, color, from, to);
        // Both halves are attempted; a run that already exists on both ends is not a
        // new cable, and reporting success would charge the player a second spool for
        // nothing. (On a single rack both calls hit the same block entity, where the
        // second is naturally the duplicate — the first having already succeeded.)
        boolean added = fromRack.putCable(cable);
        added |= toRack.putCable(cable);
        return added;
    }

    /**
     * Repaints one cable on both racks, returning whether it happened.
     *
     * <p>Colour is stored inside the cable, not in its item (§6.8), so changing it
     * means replacing the cable in both racks. Doing it here — rather than editing
     * a list entry in place — is what keeps the two halves from disagreeing.</p>
     */
    public static boolean recolor(Level level, CableConnection cable, CableColor color) {
        if (level.isClientSide()) {
            return false;
        }
        // Only a sheath can be painted. A bare conductor shows its metal, which is
        // how a player tells a bare line from an insulated one at a glance; letting
        // dye change that would destroy the only visual cue the product has.
        if (!CableColor.isPaintable(cable.insulation())) {
            return false;
        }
        CableConnection painted = cable.withColor(color);
        boolean aDone = false;
        boolean bDone = false;
        TerminalMountBlockEntity a = rackAt(level, cable.a().pos());
        if (a != null) {
            aDone = a.replaceCable(cable, painted);
        }
        TerminalMountBlockEntity b = rackAt(level, cable.b().pos());
        if (b != null) {
            bDone = b.replaceCable(cable, painted);
        }
        return aDone || bDone;
    }

    /**
     * Removes the specific cable {@code cable} from both racks, returning its item
     * stack (empty when it was not there).
     */
    public static ItemStack disconnect(Level level, CableConnection cable) {
        if (level.isClientSide()) {
            return ItemStack.EMPTY;
        }
        boolean removed = false;
        TerminalMountBlockEntity a = rackAt(level, cable.a().pos());
        if (a != null) {
            removed |= a.disconnectCable(cable);
        }
        TerminalMountBlockEntity b = rackAt(level, cable.b().pos());
        if (b != null) {
            removed |= b.disconnectCable(cable);
        }
        return removed ? CableItem.stackOf(cable) : ItemStack.EMPTY;
    }

    /**
     * Removes every cable on {@code endpoint} from both racks, returning their
     * stacks.
     */
    public static List<ItemStack> disconnectAll(Level level, TerminalEndpoint endpoint) {
        if (level.isClientSide()) {
            return List.of();
        }
        TerminalMountBlockEntity here = rackAt(level, endpoint.pos());
        if (here == null) {
            return List.of();
        }
        List<ItemStack> drops = new ArrayList<>();
        for (CableConnection cable : new ArrayList<>(here.cablesAt(endpoint.cell()))) {
            ItemStack stack = disconnect(level, cable);
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        return drops;
    }

    /**
     * The loaded rack at {@code pos}, or {@code null}.
     *
     * <p>Returning {@code null} for an unloaded chunk is the normal case for "the far
     * end is elsewhere": callers treat it as "cannot see it right now", never as
     * "delete the cable". That is what stops a long cable from being severed
     * whenever the player walks away from one end.</p>
     */
    private static TerminalMountBlockEntity rackAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof TerminalMountBlockEntity rack ? rack : null;
    }

    /** Every cable leaving the rack at {@code pos}, or an empty list. */
    public static List<CableConnection> cablesAt(Level level, BlockPos pos) {
        TerminalMountBlockEntity rack = rackAt(level, pos);
        return rack == null ? List.of() : new ArrayList<>(rack.cables());
    }

    /**
     * The world point a cable hangs from at {@code endpoint}, or {@code null} when
     * that terminal no longer exists.
     *
     * <p>Works on the <b>client</b> too — it only reads block entities and their
     * blockstates — because the renderer needs it every frame. Derived live rather
     * than stored, so a rack that is rotated after a cable was pulled carries the
     * cable to its new face instead of anchoring it to the old one.</p>
     */
    public static org.joml.Vector3f anchorOf(Level level, TerminalEndpoint endpoint) {
        if (level == null) {
            return null;
        }
        if (!(level.getBlockEntity(endpoint.pos()) instanceof TerminalMountBlockEntity rack)) {
            return null;
        }
        if (!(rack.getBlockState().getBlock()
                instanceof com.stardustindustry.stardustindustry.machine.terminal.TerminalMountBlock mount)) {
            return null;
        }
        if (!rack.hasTerminal(endpoint.cell())) {
            return null;
        }
        TerminalGrid grid = mount.grid();
        if (!grid.contains(endpoint.cell())) {
            return null;
        }
        return TerminalGeometry.cableAnchor(endpoint.pos(), rack.facing(), grid, endpoint.cell());
    }
}
