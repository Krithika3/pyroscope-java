package io.pyroscope.javaagent;

import io.pyroscope.javaagent.api.Exporter;
import io.pyroscope.javaagent.api.Logger;
import io.pyroscope.javaagent.api.ProfilingScheduler;
import io.pyroscope.javaagent.config.Config;
import io.pyroscope.javaagent.impl.*;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicReference;

public class PyroscopeAgent {
    // this is used to store the options passed to the agent
    private static final AtomicReference<Options> startOptions = new AtomicReference<>(null);

    private static final String stopLock = "";

    private static final String startLock = "";

    public static void premain(final String agentArgs,
                               final Instrumentation inst) {
        final Config config;
        try {
            config = Config.build(DefaultConfigurationProvider.INSTANCE);
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.DEBUG, "Config: %s", config);
        } catch (final Throwable e) {
            DefaultLogger.PRECONFIG_LOGGER.log(Logger.Level.ERROR, "Error starting profiler %s", e);
            return;
        }
        start(config);
    }

    public static void start() {
        start(new Config.Builder().build());
    }

    public static void start(Config config) {
        start(new Options.Builder(config).build());
    }

    public static void start(Options options) {
        synchronized (startLock) {
            Logger logger = options.logger;
            if (!startOptions.compareAndSet(null, options)) {
                logger.log(Logger.Level.ERROR, "Failed to start profiling - already started");
                return;
            }
            if (!options.config.agentEnabled) {
                logger.log(Logger.Level.INFO, "Pyroscope agent start disabled by configuration");
                return;
            }
            logger.log(Logger.Level.DEBUG, "Config: %s", options.config);
            try {
                options.scheduler.start(options.profiler);
                logger.log(Logger.Level.INFO, "Profiling started");
            } catch (final Throwable e) {
                logger.log(Logger.Level.ERROR, "Error starting profiler %s", e);
            }
        }
    }

    /**
     * stop is used to stop profiling
     */
    public static void stop() {
        synchronized (stopLock) {
            if (startOptions.get() == null) {
                return;
            }
            ProfilingScheduler scheduler = startOptions.get().scheduler;
            Logger logger = startOptions.get().logger;
            Profiler profiler = startOptions.get().profiler;

            if (logger == null) {
                return;
            }
            if (scheduler == null || profiler == null) {
                logger.log(Logger.Level.ERROR, "Failed to stop profiling - already stopped");
                return;
            }

            logger.log(Logger.Level.DEBUG, "Config: %s", startOptions.get().config);
            scheduler.stop(profiler);
            startOptions.set(null);
            logger.log(Logger.Level.INFO, "Profiling stopped");
        }
    }

    /**
     * Options allow to swap pyroscope components:
     * - io.pyroscope.javaagent.api.ProfilingScheduler
     * - org.apache.logging.log4j.Logger
     * - io.pyroscope.javaagent.api.Exporter for io.pyroscope.javaagent.impl.ContinuousProfilingScheduler
     */
    public static class Options {
        final Config config;
        final ProfilingScheduler scheduler;
        final Logger logger;
        final Profiler profiler;

        private Options(Builder b) {
            this.config = b.config;
            this.profiler = b.profiler;
            this.scheduler = b.scheduler;
            this.logger = b.logger;
        }

        public static class Builder {
            final Config config;
            final Profiler profiler;
            Exporter exporter;
            ProfilingScheduler scheduler;
            Logger logger;

            public Builder(Config config) {
                this.config = config;
                this.profiler = new Profiler(config);
            }

            public Builder setExporter(Exporter exporter) {
                this.exporter = exporter;
                return this;
            }

            public Builder setScheduler(ProfilingScheduler scheduler) {
                this.scheduler = scheduler;
                return this;
            }

            public Builder setLogger(Logger logger) {
                this.logger = logger;
                return this;
            }

            public Options build() {
                if (logger == null) {
                    logger = new DefaultLogger(config.logLevel, System.err);
                }
                if (scheduler == null) {
                    if (exporter == null) {
                        exporter = new QueuedExporter(config, new PyroscopeExporter(config, logger), logger);
                    }
                    if (config.samplingDuration == null) {
                        scheduler = new ContinuousProfilingScheduler(config, exporter, logger);
                    } else {
                        scheduler = new SamplingProfilingScheduler(config, exporter, logger);
                    }
                }
                return new Options(this);
            }
        }

    }

}
