import io.pyroscope.http.Format;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.Snapshot;
import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.impl.DefaultConfigurationProvider;
import io.pyroscope.labels.Pyroscope;
import io.pyroscope.labels.LabelsSet;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {
    public static final int N_THREADS = 8;

    public static void main(String[] args) throws InterruptedException {
        PyroscopeAgent.start(
            new PyroscopeAgent.Options.Builder(
                new Config.Builder()
                    .setApplicationName("demo.app{qweqwe=asdasd}")
                    .setServerAddress("http://localhost:4040")
                    .setFormat(Format.JFR)
                    .setLogLevel(Logger.Level.DEBUG)
                    .setLabels(mapOf("user", "tolyan"))
                    .build())
//                .setExporter(new MyStdoutExporter())
                .build()
        );
        Pyroscope.setStaticLabels(mapOf("region", "us-east-1"));

        appLogic();

        // Test this by creating a new thread, stop the agent,  sleeping for a second, and then restarting the agent.
        Thread newThread = new Thread(new Runnable() {
            public void run()
            {
                // Sleep for a second to make sure the agent has started
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                // Stop the agent after a second
                PyroscopeAgent.stop();
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                // Start the agent back up again after a second
                PyroscopeAgent.start(
                    new PyroscopeAgent.Options.Builder(
                        new Config.Builder()
                            .setApplicationName("demo.app{qweqwe=asdasd}")
                            .setServerAddress("http://localhost:4040")
                            .setFormat(Format.JFR)
                            .setLogLevel(Logger.Level.DEBUG)
                            .setLabels(mapOf("user", "tolyan"))
                            .build())
//                .setExporter(new MyStdoutExporter())
                        .build()
                );
            }});
        newThread.start();
    }

    private static void appLogic() {
        ExecutorService executors = Executors.newFixedThreadPool(N_THREADS);
        for (int i = 0; i < N_THREADS; i++) {
            executors.submit(() -> {
                Pyroscope.LabelsWrapper.run(new LabelsSet("thread_name", Thread.currentThread().getName()), () -> {
                        while (true) {
                            try {
                                fib(32L);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                );
            });
        }
    }

    private static Map<String, String> mapOf(String... args) {
        Map<String, String> staticLabels = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            staticLabels.put(args[i], args[i + 1]);
        }
        return staticLabels;
    }

    private static long fib(Long n) throws InterruptedException {
        if (n == 0L) {
            return 0L;
        }
        if (n == 1L) {
            return 1L;
        }
        Thread.sleep(100);
        return fib(n - 1) + fib(n - 2);
    }

    private static class MyStdoutExporter implements Exporter {
        @Override
        public void export(Snapshot snapshot) {
            System.out.printf("Export %d %d%n", snapshot.data.length, snapshot.labels.toByteArray().length);
        }
    }
}
