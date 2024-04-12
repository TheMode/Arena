package net.minestom.arena;

import com.sun.management.OperatingSystemMXBean;
import io.prometheus.client.*;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.GarbageCollectorExports;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import net.minestom.arena.config.ConfigHandler;
import net.minestom.arena.utils.NetworkUsage;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.entity.EntitySpawnEvent;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerPacketOutEvent;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Metrics {
    public static final Gauge ENTITIES = Gauge.build().name("entities")
            .help("Total entities alive (excluding players)").register();
    public static final Gauge GAMES_IN_PROGRESS = Gauge.build().name("games_in_progress")
            .labelNames("type").help("Games currently running").register();
    public static final Counter GAMES_PLAYED = Counter.build().name("games_played")
            .labelNames("type").help("Number of games played").register();
    public static final Summary TICK_TIME = Summary.build().name("tick_time")
            .help("ms per tick").quantile(0, 1).quantile(.5, .01).quantile(1, 0)
            .maxAgeSeconds(5).unit("ms").register();
    public static final Summary ACQUISITION_TIME = Summary.build().name("acquisition_time")
            .help("ms per acquisition").quantile(0, 1).quantile(.5, .01).quantile(1, 0)
            .maxAgeSeconds(5).unit("ms").register();
    public static final Counter EXCEPTIONS = Counter.build().name("exceptions")
            .help("Number of exceptions").labelNames("simple_name").register();
    private static final Counter PACKETS = Counter.build().name("packets").help("Number of packets by direction")
            .labelNames("direction").register();
    private static final Gauge ONLINE_PLAYERS = Gauge.build().name("online_players")
            .help("Number of currently online players").register();
    private static final Info GENERIC_INFO = Info.build().name("generic").help("Generic system information")
            .register();
    private static final OperatingSystemMXBean systemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);


    public static void init() {
        try {
            final String unknown = "unknown";
            GENERIC_INFO.info(
                    "java_version", System.getProperty("java.version", unknown),
                    "java_vendor", System.getProperty("java.vendor", unknown),
                    "os_arch", System.getProperty("os.arch", unknown),
                    "os_name", System.getProperty("os.name", unknown),
                    "os_version", System.getProperty("os.version", unknown),
                    "available_processors", ""+systemMXBean.getAvailableProcessors()
            );

            // Packets & players
            MinecraftServer.getGlobalEventHandler()
                    .addListener(PlayerPacketEvent.class, e -> Metrics.PACKETS.labels("in").inc())
                    .addListener(PlayerPacketOutEvent.class, e -> Metrics.PACKETS.labels("out").inc())
                    .addListener(AsyncPlayerConfigurationEvent.class, e -> Metrics.ONLINE_PLAYERS.inc())
                    .addListener(PlayerDisconnectEvent.class, e -> Metrics.ONLINE_PLAYERS.dec())
                    .addListener(EntitySpawnEvent.class, e -> {
                        if (!(e.getEntity() instanceof Player)) Metrics.ENTITIES.inc();
                    }).addListener(RemoveEntityFromInstanceEvent.class, e -> {
                        if (!(e.getEntity() instanceof Player)) Metrics.ENTITIES.dec();
                    });

            // Network usage
            if (NetworkUsage.checkEnabledOrExtract()) {
                NetworkUsage.resetCounters();
                NetworkCounter.build().name("network_io").help("Network usage").unit("bytes").labelNames("direction")
                        .register();
            }

            CPUGauge.build().name("cpu").help("CPU Usage").register();
            new HTTPServer(ConfigHandler.CONFIG.prometheus().port());
            new MemoryPoolsExports().register();
            new GarbageCollectorExports().register();
        } catch (IOException e) {
            MinecraftServer.getExceptionManager().handleException(e);
        }
    }

    private static class NetworkCounter extends SimpleCollector<Counter.Child> {
        private final double created = System.currentTimeMillis()/1000f;
        private final static List<String> outLabels = List.of("out");
        private final static List<String> inLabels = List.of("in");

        protected NetworkCounter(Builder b) {
            super(b);
        }

        public static class Builder extends SimpleCollector.Builder<NetworkCounter.Builder, NetworkCounter> {

            @Override
            public NetworkCounter create() {
                return new NetworkCounter(this);
            }
        }

        public static Builder build() {
            return new Builder();
        }

        @Override
        protected Counter.Child newChild() {
            return null;
        }

        @Override
        public List<MetricFamilySamples> collect() {
            List<MetricFamilySamples.Sample> samples = new ArrayList<MetricFamilySamples.Sample>();
            samples.add(new MetricFamilySamples.Sample(fullname + "_total", labelNames, outLabels, NetworkUsage.getBytesSent()));
            samples.add(new MetricFamilySamples.Sample(fullname + "_created", labelNames, outLabels, created));
            samples.add(new MetricFamilySamples.Sample(fullname + "_total", labelNames, inLabels, NetworkUsage.getBytesReceived()));
            samples.add(new MetricFamilySamples.Sample(fullname + "_created", labelNames, inLabels, created));
            return familySamplesList(Type.COUNTER, samples);
        }
    }

    private static class CPUGauge extends SimpleCollector<Gauge.Child> {

        protected CPUGauge(Builder b) {
            super(b);
        }

        public static class Builder extends SimpleCollector.Builder<CPUGauge.Builder, CPUGauge> {

            @Override
            public CPUGauge create() {
                return new CPUGauge(this);
            }
        }

        public static Builder build() {
            return new Builder();
        }

        @Override
        protected Gauge.Child newChild() {
            return new Gauge.Child();
        }

        @Override
        public List<MetricFamilySamples> collect() {
            List<MetricFamilySamples.Sample> samples = new ArrayList<MetricFamilySamples.Sample>(1);
            samples.add(new MetricFamilySamples.Sample(fullname, labelNames, Collections.emptyList(), systemMXBean.getProcessCpuLoad()));
            return familySamplesList(Type.GAUGE, samples);
        }
    }
}
