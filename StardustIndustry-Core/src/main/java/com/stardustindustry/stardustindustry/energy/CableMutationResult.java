package com.stardustindustry.stardustindustry.energy;

import java.util.Objects;

/** Result of an authoritative cable-store mutation. */
public record CableMutationResult(Status status, CableConnection cable) {

    public CableMutationResult {
        Objects.requireNonNull(status, "status");
    }

    public static CableMutationResult success(CableConnection cable) {
        return new CableMutationResult(Status.SUCCESS, Objects.requireNonNull(cable, "cable"));
    }

    public static CableMutationResult failure(Status status) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("Use success() for successful mutations");
        }
        return new CableMutationResult(status, null);
    }

    public static CableMutationResult unchanged(Status status, CableConnection cable) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("Use success() for successful mutations");
        }
        return new CableMutationResult(status, cable);
    }

    public boolean changed() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        NO_CHANGE,
        NOT_FOUND,
        CLIENT_SIDE,
        SAME_ENDPOINT,
        INVALID_PRODUCT,
        ENDPOINT_UNAVAILABLE,
        TERMINAL_MISSING,
        TOO_FAR,
        DUPLICATE_ENDPOINTS,
        WOULD_CREATE_CYCLE,
        NOT_PAINTABLE,
        PRODUCT_MISMATCH,
        TOMBSTONED,
        UNSUPPORTED_DATA_VERSION
    }
}
