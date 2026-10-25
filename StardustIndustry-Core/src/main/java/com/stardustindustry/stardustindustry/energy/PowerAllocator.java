package com.stardustindustry.stardustindustry.energy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Exact, stable proportional allocation for whole-watt budgets. */
public final class PowerAllocator {
    private PowerAllocator() {}

    public static List<Long> proportional(long availableWatts, List<Long> requests) {
        long available = Math.max(0L, availableWatts);
        List<Long> normalized = requests.stream().map(value -> Math.max(0L, value)).toList();
        BigInteger totalRequest = normalized.stream()
                .map(BigInteger::valueOf)
                .reduce(BigInteger.ZERO, BigInteger::add);
        if (available == 0L || totalRequest.signum() == 0) {
            return normalized.stream().map(ignored -> 0L).toList();
        }

        long distributable = totalRequest.compareTo(BigInteger.valueOf(available)) <= 0
                ? totalRequest.longValueExact() : available;
        BigInteger total = BigInteger.valueOf(distributable);
        List<Long> shares = new ArrayList<>(normalized.size());
        long allocated = 0L;
        for (long request : normalized) {
            long share = total.multiply(BigInteger.valueOf(request))
                    .divide(totalRequest)
                    .longValueExact();
            shares.add(share);
            allocated += share;
        }

        long remainder = distributable - allocated;
        while (remainder > 0L) {
            boolean progressed = false;
            for (int index = 0; index < shares.size() && remainder > 0L; index++) {
                if (shares.get(index) >= normalized.get(index)) {
                    continue;
                }
                shares.set(index, shares.get(index) + 1L);
                remainder--;
                progressed = true;
            }
            if (!progressed) {
                throw new IllegalStateException("Unable to distribute proportional power remainder");
            }
        }
        return List.copyOf(shares);
    }
}
