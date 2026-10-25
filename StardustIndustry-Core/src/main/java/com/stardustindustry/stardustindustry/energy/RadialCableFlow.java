package com.stardustindustry.stardustindustry.energy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Solves branch current and resistive loss for a forest with one root per conductor. */
public final class RadialCableFlow {
    private static final double BALANCE_EPSILON = 1.0e-9;

    private RadialCableFlow() {}

    public static <N, E> Result<E> solve(Collection<N> conductorRoots,
                                         Collection<Branch<N, E>> branches,
                                         Map<N, Double> nodeNetCurrentAmps) {
        Set<N> roots = new HashSet<>(conductorRoots);
        if (roots.isEmpty() || roots.size() != conductorRoots.size()) {
            return Result.unsupported();
        }

        Map<N, List<Branch<N, E>>> adjacent = new LinkedHashMap<>();
        Set<E> edgeIds = new HashSet<>();
        for (N root : conductorRoots) {
            adjacent.computeIfAbsent(Objects.requireNonNull(root), ignored -> new ArrayList<>());
        }
        for (Branch<N, E> branch : branches) {
            if (branch.a().equals(branch.b()) || !edgeIds.add(branch.edge())) {
                return Result.unsupported();
            }
            adjacent.computeIfAbsent(branch.a(), ignored -> new ArrayList<>()).add(branch);
            adjacent.computeIfAbsent(branch.b(), ignored -> new ArrayList<>()).add(branch);
        }

        Set<N> visited = new HashSet<>();
        Map<E, Double> currentByEdge = new LinkedHashMap<>();
        double totalLossWatts = 0.0;
        for (N root : conductorRoots) {
            if (!visited.add(root)) {
                return Result.unsupported();
            }

            List<N> order = new ArrayList<>();
            Map<N, Branch<N, E>> parentEdge = new HashMap<>();
            Map<N, N> parentNode = new HashMap<>();
            ArrayDeque<N> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                N node = stack.pop();
                order.add(node);
                Branch<N, E> incoming = parentEdge.get(node);
                for (Branch<N, E> branch : adjacent.getOrDefault(node, List.of())) {
                    if (branch == incoming) {
                        continue;
                    }
                    N next = branch.other(node);
                    if (!visited.add(next)) {
                        return Result.unsupported();
                    }
                    parentEdge.put(next, branch);
                    parentNode.put(next, node);
                    stack.push(next);
                }
            }

            Map<N, Double> subtreeCurrent = new HashMap<>();
            double absoluteNetCurrent = 0.0;
            for (N node : order) {
                double net = nodeNetCurrentAmps.getOrDefault(node, 0.0);
                if (!Double.isFinite(net)) {
                    return Result.unsupported();
                }
                subtreeCurrent.put(node, net);
                absoluteNetCurrent += Math.abs(net);
            }
            for (int index = order.size() - 1; index >= 0; index--) {
                N node = order.get(index);
                Branch<N, E> branch = parentEdge.get(node);
                if (branch == null) {
                    continue;
                }
                double subtree = subtreeCurrent.get(node);
                double current = Math.abs(subtree);
                currentByEdge.put(branch.edge(), current);
                totalLossWatts += ElectricalUnits.resistiveLossWatts(current,
                        branch.resistanceOhms());
                N parent = parentNode.get(node);
                subtreeCurrent.merge(parent, subtree, Double::sum);
            }

            double imbalance = Math.abs(subtreeCurrent.getOrDefault(root, 0.0));
            double tolerance = Math.max(BALANCE_EPSILON, absoluteNetCurrent * BALANCE_EPSILON);
            if (imbalance > tolerance || !Double.isFinite(totalLossWatts)) {
                return Result.unsupported();
            }
        }

        if (visited.size() != adjacent.size() || !visited.containsAll(nodeNetCurrentAmps.keySet())) {
            return Result.unsupported();
        }
        return new Result<>(Status.SUPPORTED,
                Collections.unmodifiableMap(new LinkedHashMap<>(currentByEdge)), totalLossWatts);
    }

    public enum Status {
        SUPPORTED,
        UNSUPPORTED_TOPOLOGY
    }

    public record Branch<N, E>(E edge, N a, N b, double resistanceOhms) {
        public Branch {
            Objects.requireNonNull(edge);
            Objects.requireNonNull(a);
            Objects.requireNonNull(b);
            if (!Double.isFinite(resistanceOhms) || resistanceOhms < 0.0) {
                throw new IllegalArgumentException("Resistance must be finite and non-negative");
            }
        }

        N other(N node) {
            if (a.equals(node)) {
                return b;
            }
            if (b.equals(node)) {
                return a;
            }
            throw new IllegalArgumentException("Node is not incident to this branch");
        }
    }

    public record Result<E>(Status status, Map<E, Double> currentAmpsByEdge, double lossWatts) {
        private static <E> Result<E> unsupported() {
            return new Result<>(Status.UNSUPPORTED_TOPOLOGY, Map.of(), 0.0);
        }

        public boolean supported() {
            return status == Status.SUPPORTED;
        }
    }
}
