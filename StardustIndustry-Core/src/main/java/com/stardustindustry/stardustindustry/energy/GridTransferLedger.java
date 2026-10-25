package com.stardustindustry.stardustindustry.energy;

import java.util.IdentityHashMap;
import java.util.Map;

/** Enforces per-tick power budgets for ports backed by the same energy account. */
final class GridTransferLedger {
    private final Map<Object, Budget> budgets = new IdentityHashMap<>();

    long receivableWatts(IElectricalPort port) {
        return available(port, port.requestedPowerWatts(), Direction.RECEIVE);
    }

    long extractableWatts(IElectricalPort port) {
        return available(port, port.availablePowerWatts(), Direction.EXTRACT);
    }

    void recordReceived(IElectricalPort port, long watts) {
        record(port, watts, Direction.RECEIVE);
    }

    void recordExtracted(IElectricalPort port, long watts) {
        record(port, watts, Direction.EXTRACT);
    }

    private long available(IElectricalPort port, long requested, Direction direction) {
        Budget budget = budget(port);
        return Math.min(Math.max(0L, requested),
                budget.remainingWatts(direction, port.maxPowerWatts()));
    }

    private void record(IElectricalPort port, long watts, Direction direction) {
        if (watts <= 0L) {
            return;
        }
        budget(port).record(watts, direction, port.maxPowerWatts());
    }

    private Budget budget(IElectricalPort port) {
        Budget budget = budgets.computeIfAbsent(port.energyAccount(),
                ignored -> new Budget(port.allowsSameTickPassThrough()));
        if (budget.passThrough != port.allowsSameTickPassThrough()) {
            throw new IllegalStateException("Ports sharing an energy account disagree on transfer policy");
        }
        return budget;
    }

    private enum Direction {
        RECEIVE,
        EXTRACT
    }

    private static final class Budget {
        private static final long UNINITIALIZED = -1L;

        private final boolean passThrough;
        private long sharedRemainingWatts = UNINITIALIZED;
        private long receiveRemainingWatts = UNINITIALIZED;
        private long extractRemainingWatts = UNINITIALIZED;
        private Direction direction;

        private Budget(boolean passThrough) {
            this.passThrough = passThrough;
        }

        private long remainingWatts(Direction requestedDirection, long maxPowerWatts) {
            long maximum = Math.max(0L, maxPowerWatts);
            if (!passThrough) {
                if (direction != null && direction != requestedDirection) {
                    return 0L;
                }
                if (sharedRemainingWatts == UNINITIALIZED) {
                    sharedRemainingWatts = maximum;
                }
                return sharedRemainingWatts;
            }
            if (requestedDirection == Direction.RECEIVE) {
                if (receiveRemainingWatts == UNINITIALIZED) {
                    receiveRemainingWatts = maximum;
                }
                return receiveRemainingWatts;
            }
            if (extractRemainingWatts == UNINITIALIZED) {
                extractRemainingWatts = maximum;
            }
            return extractRemainingWatts;
        }

        private void record(long watts, Direction recordedDirection, long maxPowerWatts) {
            long remaining = remainingWatts(recordedDirection, maxPowerWatts);
            if (!passThrough && direction != null && direction != recordedDirection) {
                throw new IllegalStateException("An energy account cannot charge and discharge in one tick");
            }
            direction = recordedDirection;
            long updated = Math.max(0L, remaining - watts);
            if (!passThrough) {
                sharedRemainingWatts = updated;
            } else if (recordedDirection == Direction.RECEIVE) {
                receiveRemainingWatts = updated;
            } else {
                extractRemainingWatts = updated;
            }
        }
    }
}
