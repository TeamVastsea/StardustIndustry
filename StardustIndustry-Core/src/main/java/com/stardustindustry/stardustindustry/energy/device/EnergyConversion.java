package com.stardustindustry.stardustindustry.energy.device;

import java.math.BigInteger;

/** Whole-joule conversion helpers used at exactly one device boundary. */
final class EnergyConversion {
    private static final long EFFICIENCY_SCALE = 1_000_000L;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private EnergyConversion() {}

    static long outputForInput(long input, double efficiency) {
        return convertInput(input, efficiency, 0L).amount();
    }

    static long inputForOutput(long output, double efficiency) {
        return consumeInputForOutput(output, efficiency, 0L).amount();
    }

    /**
     * Converts whole input units while carrying fractional output into the next call.
     * The returned remainder uses the reduced efficiency denominator as its unit.
     */
    static Result convertInput(long input, double efficiency, long remainder) {
        Rate rate = rate(efficiency);
        long normalized = normalizeRemainder(remainder, rate);
        if (input <= 0L || rate.numerator() == 0L) {
            return new Result(0L, normalized);
        }
        BigInteger units = BigInteger.valueOf(input).multiply(BigInteger.valueOf(rate.numerator()))
                .add(BigInteger.valueOf(normalized));
        BigInteger[] converted = units.divideAndRemainder(BigInteger.valueOf(rate.denominator()));
        return new Result(saturatedLong(converted[0]), converted[1].longValueExact());
    }

    /**
     * Calculates the minimum whole input needed for an exact whole output. Any excess
     * fractional output is retained, so repeated small transfers pay the same total
     * conversion loss as one large transfer.
     */
    static Result consumeInputForOutput(long output, double efficiency, long remainder) {
        Rate rate = rate(efficiency);
        long normalized = normalizeRemainder(remainder, rate);
        if (output <= 0L || rate.numerator() == 0L) {
            return new Result(0L, normalized);
        }
        BigInteger outputUnits = BigInteger.valueOf(output)
                .multiply(BigInteger.valueOf(rate.denominator()));
        BigInteger requiredUnits = outputUnits.subtract(BigInteger.valueOf(normalized));
        BigInteger numerator = BigInteger.valueOf(rate.numerator());
        BigInteger[] division = requiredUnits.divideAndRemainder(numerator);
        BigInteger input = division[1].signum() == 0
                ? division[0] : division[0].add(BigInteger.ONE);
        if (input.compareTo(LONG_MAX) > 0) {
            return new Result(Long.MAX_VALUE, normalized);
        }
        BigInteger nextRemainder = BigInteger.valueOf(normalized)
                .add(input.multiply(numerator))
                .subtract(outputUnits);
        return new Result(input.longValueExact(), nextRemainder.longValueExact());
    }

    /** Maximum whole input that fits without exceeding the output-side capacity. */
    static long maxInputForOutputCapacity(long outputCapacity, double efficiency, long remainder) {
        Rate rate = rate(efficiency);
        if (outputCapacity <= 0L || rate.numerator() == 0L) {
            return 0L;
        }
        long normalized = normalizeRemainder(remainder, rate);
        // floor((input * numerator + remainder) / denominator) <= capacity
        // is equivalent to input * numerator < (capacity + 1) * denominator - remainder.
        BigInteger availableUnits = BigInteger.valueOf(outputCapacity).add(BigInteger.ONE)
                .multiply(BigInteger.valueOf(rate.denominator()))
                .subtract(BigInteger.ONE)
                .subtract(BigInteger.valueOf(normalized));
        if (availableUnits.signum() <= 0) {
            return 0L;
        }
        return saturatedLong(availableUnits.divide(BigInteger.valueOf(rate.numerator())));
    }

    static long normalizeRemainder(long remainder, double efficiency) {
        return normalizeRemainder(remainder, rate(efficiency));
    }

    private static long normalizeRemainder(long remainder, Rate rate) {
        return Math.max(0L, Math.min(rate.denominator() - 1L, remainder));
    }

    private static Rate rate(double efficiency) {
        if (!Double.isFinite(efficiency) || efficiency <= 0.0) {
            return new Rate(0L, 1L);
        }
        long numerator = Math.max(1L,
                Math.round(Math.min(1.0, efficiency) * EFFICIENCY_SCALE));
        long divisor = gcd(numerator, EFFICIENCY_SCALE);
        return new Rate(numerator / divisor, EFFICIENCY_SCALE / divisor);
    }

    private static long gcd(long a, long b) {
        while (b != 0L) {
            long next = a % b;
            a = b;
            b = next;
        }
        return a;
    }

    private static long saturatedLong(BigInteger value) {
        return value.compareTo(LONG_MAX) >= 0 ? Long.MAX_VALUE : value.longValueExact();
    }

    record Result(long amount, long remainder) {}

    private record Rate(long numerator, long denominator) {}
}
