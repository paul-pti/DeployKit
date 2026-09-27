package com.deploykit.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ProcessCommandRunner implements CommandRunner {

    private static final Duration READER_JOIN_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public CommandResult run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        // The argument list is passed as-is to the OS: no shell, so no interpretation of metacharacters.
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = Thread.startVirtualThread(() -> drain(process.getInputStream(), output));

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
        }
        reader.join(READER_JOIN_TIMEOUT);

        return new CommandResult(finished ? process.exitValue() : -1, output.toString(StandardCharsets.UTF_8), !finished);
    }

    private static void drain(InputStream in, ByteArrayOutputStream out) {
        try (in) {
            in.transferTo(out);
        } catch (IOException e) {
            // The process was killed or closed its output; whatever was read so far is kept.
        }
    }
}
