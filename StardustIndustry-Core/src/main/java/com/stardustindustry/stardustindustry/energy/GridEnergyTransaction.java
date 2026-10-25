package com.stardustindustry.stardustindustry.energy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Preflights every endpoint before committing one tick of native-grid energy. */
final class GridEnergyTransaction {
    private final Remainders remainders;
    private final List<Transfer> transfers = new ArrayList<>();
    private final Map<Object, Direction> accounts = new IdentityHashMap<>();

    GridEnergyTransaction(Remainders remainders) {
        this.remainders = Objects.requireNonNull(remainders);
    }

    void extract(IElectricalPort port, long watts) {
        add(port, watts, Direction.EXTRACT);
    }

    void receive(IElectricalPort port, long watts) {
        add(port, watts, Direction.RECEIVE);
    }

    private void add(IElectricalPort port, long watts, Direction direction) {
        if (watts <= 0L) {
            return;
        }
        Object account = Objects.requireNonNull(port.energyAccount(), "energyAccount");
        if (accounts.put(account, direction) != null) {
            throw new IllegalArgumentException("An energy account may appear only once per transaction");
        }
        ElectricalUnits.TickEnergy energy = ElectricalUnits.tickEnergy(
                watts, remainders.get(account, direction));
        transfers.add(new Transfer(port, account, direction, watts, energy));
    }

    boolean commit() {
        for (Transfer transfer : transfers) {
            long accepted = transfer.simulate();
            if (accepted != transfer.energy().joules()) {
                return false;
            }
        }
        for (Direction direction : List.of(Direction.EXTRACT, Direction.RECEIVE)) {
            for (Transfer transfer : transfers) {
                if (transfer.direction() != direction) {
                    continue;
                }
                long accepted = transfer.commit();
                if (accepted != transfer.energy().joules()) {
                    throw new IllegalStateException("Electrical port violated its successful simulation");
                }
            }
        }
        for (Transfer transfer : transfers) {
            remainders.set(transfer.account(), transfer.direction(),
                    transfer.energy().remainderWattTicks());
        }
        return true;
    }

    enum Direction {
        RECEIVE,
        EXTRACT
    }

    static final class Remainders {
        private final Map<Object, long[]> byAccount = new IdentityHashMap<>();

        void retainAccounts(Collection<?> accounts) {
            var retained = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            retained.addAll(accounts);
            byAccount.keySet().removeIf(account -> !retained.contains(account));
        }

        long get(Object account, Direction direction) {
            long[] values = byAccount.get(account);
            return values == null ? 0L : values[direction.ordinal()];
        }

        void set(Object account, Direction direction, long value) {
            if (value == 0L) {
                long[] values = byAccount.get(account);
                if (values == null) {
                    return;
                }
                values[direction.ordinal()] = 0L;
                if (values[Direction.RECEIVE.ordinal()] == 0L
                        && values[Direction.EXTRACT.ordinal()] == 0L) {
                    byAccount.remove(account);
                }
                return;
            }
            byAccount.computeIfAbsent(account, ignored -> new long[Direction.values().length])
                    [direction.ordinal()] = value;
        }
    }

    private record Transfer(IElectricalPort port, Object account, Direction direction,
                            long watts, ElectricalUnits.TickEnergy energy) {
        long simulate() {
            return direction == Direction.EXTRACT
                    ? port.extractJoules(energy.joules(), true)
                    : port.receiveJoules(energy.joules(), true);
        }

        long commit() {
            return direction == Direction.EXTRACT
                    ? port.extractJoules(energy.joules(), false)
                    : port.receiveJoules(energy.joules(), false);
        }
    }
}
