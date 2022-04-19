package net.minestom.arena.game.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.minestom.arena.Lobby;
import net.minestom.arena.Messenger;
import net.minestom.arena.feature.Feature;
import net.minestom.arena.feature.Features;
import net.minestom.arena.game.SingleInstanceArena;
import net.minestom.arena.group.Group;
import net.minestom.arena.utils.FullbrightDimension;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.Player;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public final class MobArena implements SingleInstanceArena {
    private static final List<Function<Integer, EntityCreature>> MOB_GENERATION_LAMBDAS = List.of(
            ZombieMob::new,
            SkeletonMob::new
    );

    public static final class MobArenaInstance extends InstanceContainer {
        public MobArenaInstance() {
            super(UUID.randomUUID(), FullbrightDimension.INSTANCE);
            setGenerator(unit -> {
                unit.modifier().fillHeight(0, 40, Block.SAND);
                unit.modifier().fill(new Vec(-10, 40, -10), new Vec(10, 40, 10), Block.SMOOTH_QUARTZ);
            });

            for (int x = -10; x < 10; x++) {
                for (int z = -10; z < 10; z++) {
                    setBlock(x, 39, z, Block.RED_SAND);
                }
            }
        }
    }

    private final Group group;
    private final Instance arenaInstance = new MobArenaInstance();

    private int stage = 0;

    public void nextStage() {
        stage++;
        for (int i = 0; i < stage; i++) {
            EntityCreature creature = findMob(stage, arenaInstance.eventNode());
            creature.setInstance(arenaInstance, new Pos(
                    ThreadLocalRandom.current().nextInt(-10, 10),
                    41,
                    ThreadLocalRandom.current().nextInt(-10, 10)
            ));
        }
        arenaInstance.showTitle(Title.title(Component.text("Stage " + stage), Component.empty()));
        arenaInstance.sendMessage(Component.text("Stage " + stage));
    }

    public MobArena(Group group) {
        this.group = group;
        arenaInstance.eventNode().addListener(EntityDeathEvent.class, (event) -> {
            for (Entity entity : this.arenaInstance.getEntities()) {
                if (entity instanceof EntityCreature creature && !(creature.isDead())) {
                    // TODO give money;
                    return; // round hasn't ended yet
                }
            }
            nextStage();
        });

        arenaInstance.eventNode().addListener(PlayerDeathEvent.class, event -> {
            event.getPlayer().setInstance(Lobby.INSTANCE);
            Messenger.info(event.getPlayer(), "You died. Your last stage was " + stage);
        });
    }

    @Override
    public void start() {
        nextStage();
    }

    @Override
    public @NotNull Group group() {
        return group;
    }

    @Override
    public @NotNull Instance instance() {
        return arenaInstance;
    }

    @Override
    public @NotNull Pos spawnPosition(@NotNull Player player) {
        return new Pos(0, 41, 0);
    }

    @Override
    public @NotNull List<Feature> features() {
        return List.of(Features.combat());
    }

    static EntityCreature findMob(int level) {
        Function<Integer, EntityCreature> randomMobGenerator = MOB_GENERATION_LAMBDAS.get(
                ThreadLocalRandom.current().nextInt(MOB_GENERATION_LAMBDAS.size()) % MOB_GENERATION_LAMBDAS.size()
        );
        return randomMobGenerator.apply(level);
    }
}
