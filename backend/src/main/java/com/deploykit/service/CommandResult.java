package com.deploykit.service;

/** Outcome of an external command: exit code and combined stdout/stderr. */
public record CommandResult(int exitCode, String output, boolean timedOut) {

    public boolean success() {
        return !timedOut && exitCode == 0;
    }
}
