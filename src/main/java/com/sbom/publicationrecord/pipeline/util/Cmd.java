package com.sbom.publicationrecord.pipeline.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class Cmd {
    public record Result(int exitCode, String stdout, String stderr) {}
    private Cmd() {}
    public static Result run(List<String> command, Duration timeout) {
        return run(command, timeout, Map.of());
    }

    public static Result run(List<String> command, Duration timeout, Map<String, String> environment) {
        try{
            ProcessBuilder pd = new ProcessBuilder(command);
            if (environment != null && !environment.isEmpty()) {
                pd.environment().putAll(environment);
            }
            Process p = pd.start();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();

            Thread t1 = pipesAsync(p.getInputStream(), out);
            Thread t2 = pipesAsync(p.getErrorStream(), err);

            boolean done = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if(!done){
                p.destroyForcibly();
                throw new IllegalStateException("Command timed out: " + String.join(" ", command));
            }
            t1.join(2000);
            t2.join(2000);

            return new Result(
                    p.exitValue(),
                    out.toString(StandardCharsets.UTF_8),
                    err.toString(StandardCharsets.UTF_8)
            );

        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to run command: " + String.join(" ", command), e);
        }

    }


    private static Thread pipesAsync(InputStream in, ByteArrayOutputStream out) {
        Thread t = new Thread(() -> {
            try(in; out) {
                in.transferTo(out);

            }catch (Exception ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        return t;

    }
}
