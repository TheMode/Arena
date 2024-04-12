package net.minestom.arena;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.arena.game.ArenaCommand;
import net.minestom.arena.game.mob.MobTestCommand;
import net.minestom.arena.group.GroupCommand;
import net.minestom.arena.group.GroupEvent;
import net.minestom.arena.lobby.Lobby;
import net.minestom.arena.utils.ResourceUtils;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.audience.Audiences;
import net.minestom.server.command.CommandManager;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.extras.lan.OpenToLAN;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.monitoring.TickMonitor;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.utils.MathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static net.minestom.arena.config.ConfigHandler.CONFIG;

final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        if (CONFIG.prometheus().enabled()) Metrics.init();

        try {
            ResourceUtils.extractResource("lobby");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Commands
        {
            CommandManager manager = MinecraftServer.getCommandManager();
            manager.setUnknownCommandCallback((sender, c) -> Messenger.warn(sender, "Command not found."));
            manager.register(new GroupCommand());
            manager.register(new ArenaCommand());
            manager.register(new MobTestCommand());
            SimpleCommands.register(manager);
        }

        // Events
        {
            GlobalEventHandler handler = MinecraftServer.getGlobalEventHandler();

            // Group events
            GroupEvent.hook(handler);
            // Server list
            ServerList.hook(handler);

            // Login
            handler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
                final Player player = event.getPlayer();
                event.setSpawningInstance(Lobby.INSTANCE);
                player.setRespawnPoint(new Pos(0.5, 16, 0.5));

                if (CONFIG.permissions().operators().contains(player.getUsername())) {
                    player.setPermissionLevel(4);
                }

                Audiences.all().sendMessage(Component.text(
                        player.getUsername() + " has joined",
                        NamedTextColor.GREEN
                ));
            });

            handler.addListener(PlayerSpawnEvent.class, event -> {
                if (!event.isFirstSpawn()) return;
                final Player player = event.getPlayer();
                Messenger.info(player, "Welcome to Minestom Arena, use /arena to play!");
                player.setGameMode(GameMode.ADVENTURE);
                player.playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 1f, 1f));
                player.setEnableRespawnScreen(false);
            });

            // Logout
            handler.addListener(PlayerDisconnectEvent.class, event -> Audiences.all().sendMessage(Component.text(
                    event.getPlayer().getUsername() + " has left",
                    NamedTextColor.RED
            )));

            // Chat
            handler.addListener(PlayerChatEvent.class, chatEvent -> {
                chatEvent.setChatFormat((event) -> Component.text(event.getEntity().getUsername())
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY)
                                .append(Component.text(event.getMessage(), NamedTextColor.WHITE))));
            });

            // Monitoring
            AtomicReference<TickMonitor> lastTick = new AtomicReference<>();
            handler.addListener(ServerTickMonitorEvent.class, event -> {
                final TickMonitor monitor = event.getTickMonitor();
                Metrics.TICK_TIME.observe(monitor.getTickTime());
                Metrics.ACQUISITION_TIME.observe(monitor.getAcquisitionTime());
                lastTick.set(monitor);
            });
            MinecraftServer.getExceptionManager().setExceptionHandler(e -> {
                LOGGER.error("Global exception handler", e);
                Metrics.EXCEPTIONS.labels(e.getClass().getSimpleName()).inc();
            });

            // Header/footer
            MinecraftServer.getSchedulerManager().scheduleTask(() -> {
                Collection<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers();
                if (players.isEmpty()) return;

                final Runtime runtime = Runtime.getRuntime();
                final TickMonitor tickMonitor = lastTick.get();
                final long ramUsage = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;

                final Component header = Component.newline()
                        .append(Component.text("Minestom Arena", Messenger.PINK_COLOR))
                        .append(Component.newline()).append(Component.text("Players: " + players.size()))
                        .append(Component.newline()).append(Component.newline())
                        .append(Component.text("RAM USAGE: " + ramUsage + " MB", NamedTextColor.GRAY).append(Component.newline())
                                .append(Component.text("TICK TIME: " + MathUtils.round(tickMonitor.getTickTime(), 2) + "ms", NamedTextColor.GRAY))).append(Component.newline());
                final Component footer = Component.newline().append(Component.text("Project: minestom.net").append(Component.newline())
                                .append(Component.text("    Source: github.com/Minestom/Minestom    ", Messenger.ORANGE_COLOR)).append(Component.newline())
                                .append(Component.text("Arena: github.com/Minestom/Arena", Messenger.ORANGE_COLOR)))
                        .append(Component.newline());

                Audiences.players().sendPlayerListHeaderAndFooter(header, footer);

            }, TaskSchedule.tick(10), TaskSchedule.tick(10));
        }

        if (CONFIG.proxy().enabled()) {
            VelocityProxy.enable(CONFIG.proxy().secret());
        } else {
            OpenToLAN.open();
            if (CONFIG.server().mojangAuth()) MojangAuth.init();
        }

        minecraftServer.start(CONFIG.server().address());
        System.out.println("Server startup done! Using configuration " + CONFIG);
    }
}
