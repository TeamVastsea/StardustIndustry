package com.stardustindustry.stardustindustry.machine.terminal;

import java.util.Objects;
import java.util.function.Function;

import com.stardustindustry.stardustindustry.energy.CableConnection;

/**
 * Common-side entry point for the client-owned cable aim test.
 *
 * <p>The block interaction code is loaded on dedicated servers, while the
 * camera and local player only exist on clients. The client installs its picker
 * during setup so common classes never link against client-only Minecraft
 * classes.</p>
 */
public final class CablePicking {

    private static volatile Function<TerminalMountBlockEntity, CableConnection> clientPicker = mount -> null;

    private CablePicking() {}

    /** Installs the picker implemented by the client distribution. */
    public static void installClientPicker(Function<TerminalMountBlockEntity, CableConnection> picker) {
        clientPicker = Objects.requireNonNull(picker, "picker");
    }

    /** Returns the cable under the local player's view ray, if any. */
    public static CableConnection pick(TerminalMountBlockEntity mount) {
        return clientPicker.apply(mount);
    }
}
