package com.deploykit.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Runs an external command without a shell; abstracted so callers can be tested without the real binary. */
public interface CommandRunner {

    CommandResult run(List<String> command, Duration timeout) throws IOException, InterruptedException;
}
