package com.stardustindustry.stardustindustry.energy;

import java.util.List;

/** Chooses which energized port establishes voltage for one exact circuit. */
final class GridSourcePolicy {
    private GridSourcePolicy() {}

    static Selection select(List<IElectricalPort> ports) {
        List<IElectricalPort> dedicated = ports.stream()
                .filter(port -> port.electricalRole() == ElectricalPortRole.SOURCE)
                .filter(IElectricalPort::enabled)
                .filter(port -> port.availablePowerWatts() > 0L)
                .toList();
        if (dedicated.size() > 1) {
            return new Selection(Kind.CONFLICT, null);
        }
        if (dedicated.size() == 1) {
            return new Selection(Kind.DEDICATED_SOURCE, dedicated.get(0));
        }
        return ports.stream()
                .filter(port -> port.electricalRole() == ElectricalPortRole.STORAGE)
                .filter(IElectricalPort::enabled)
                .filter(port -> port.availablePowerWatts() > 0L)
                .findFirst()
                .map(port -> new Selection(Kind.STORAGE_ISLAND, port))
                .orElseGet(() -> new Selection(Kind.NONE, null));
    }

    enum Kind {
        NONE,
        DEDICATED_SOURCE,
        STORAGE_ISLAND,
        CONFLICT
    }

    record Selection(Kind kind, IElectricalPort voltageSource) {}
}
