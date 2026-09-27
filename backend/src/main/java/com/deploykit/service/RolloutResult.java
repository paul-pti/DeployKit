package com.deploykit.service;

/** Result of waiting for a rollout: success, or a human-readable reason for the failure. */
public record RolloutResult(boolean success, String message) {

    static RolloutResult ok() {
        return new RolloutResult(true, null);
    }

    static RolloutResult failed(String message) {
        return new RolloutResult(false, message);
    }
}
