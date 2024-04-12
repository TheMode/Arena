package net.minestom.arena.lobby;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.minestom.arena.Messenger;
import net.minestom.arena.game.ArenaCommand;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.*;
import net.minestom.server.entity.ai.GoalSelector;
import net.minestom.server.entity.ai.target.ClosestEntityTarget;
import net.minestom.server.entity.metadata.PlayerMeta;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

// https://gist.github.com/iam4722202468/36630043ca89e786bb6318e296f822f8
final class NPC extends EntityCreature {
    private final String name;
    private final PlayerSkin skin;
    private final Consumer<Player> onClick;

    NPC(@NotNull String name, @NotNull PlayerSkin skin, @NotNull Instance instance,
        @NotNull Point spawn, @NotNull Consumer<Player> onClick) {

        super(EntityType.PLAYER);
        this.name = name;
        this.skin = skin;
        this.onClick = onClick;

        final PlayerMeta meta = (PlayerMeta) getEntityMeta();
        meta.setNotifyAboutChanges(false);
        meta.setCapeEnabled(false);
        meta.setJacketEnabled(true);
        meta.setLeftSleeveEnabled(true);
        meta.setRightSleeveEnabled(true);
        meta.setLeftLegEnabled(true);
        meta.setRightLegEnabled(true);
        meta.setHatEnabled(true);
        meta.setNotifyAboutChanges(true);

        addAIGroup(
            List.of(new LookAtPlayerGoal(this)),
            List.of(new ClosestEntityTarget(this, 15, entity -> entity instanceof Player))
        );

        setInstance(instance, spawn);
    }

    public void handle(@NotNull EntityAttackEvent event) {
        if (event.getTarget() != this) return;
        if (!(event.getEntity() instanceof Player player)) return;

        player.playSound(Sound.sound()
                .type(SoundEvent.BLOCK_NOTE_BLOCK_PLING)
                .pitch(2)
                .build(), event.getTarget());
        onClick.accept(player);
    }

    public void handle(@NotNull PlayerEntityInteractEvent event) {
        if (event.getTarget() != this) return;
        if (event.getHand() != Player.Hand.MAIN) return; // Prevent duplicating event

        event.getEntity().playSound(Sound.sound()
                .type(SoundEvent.BLOCK_NOTE_BLOCK_PLING)
                .pitch(2)
                .build(), event.getTarget());
        onClick.accept(event.getEntity());
    }

    @Override
    public void updateNewViewer(@NotNull Player player) {
        // Required to spawn player
        final List<PlayerInfoUpdatePacket.Property> properties = List.of(
                new PlayerInfoUpdatePacket.Property("textures", skin.textures(), skin.signature())
        );
        player.sendPacket(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                new PlayerInfoUpdatePacket.Entry(
                        getUuid(), name, properties, false, 0, GameMode.SURVIVAL, null,
                        null)
                )
        );

        super.updateNewViewer(player);
    }

    private static final class LookAtPlayerGoal extends GoalSelector {
        private Entity target;

        public LookAtPlayerGoal(EntityCreature entityCreature) {
            super(entityCreature);
        }

        @Override
        public boolean shouldStart() {
            target = findTarget();
            return target != null;
        }

        @Override
        public void start() {}

        @Override
        public void tick(long time) {
            if (entityCreature.getDistanceSquared(target) > 225 ||
                    entityCreature.getInstance() != target.getInstance()) {
                target = null;
                return;
            }

            entityCreature.lookAt(target);
        }

        @Override
        public boolean shouldEnd() {
            return target == null;
        }

        @Override
        public void end() {}
    }

    public static List<NPC> spawnNPCs(@NotNull Instance instance) {
        try {
            final java.util.Map<String, PlayerSkin> skins = new HashMap<>();
            final Gson gson = new Gson();
            final JsonObject root = gson.fromJson(new String(Lobby.class.getResourceAsStream("/skins.json")
                    .readAllBytes()), JsonObject.class);

            for (JsonElement skin : root.getAsJsonArray("skins")) {
                final JsonObject object = skin.getAsJsonObject();
                final String owner = object.get("owner").getAsString();
                final String value = object.get("value").getAsString();
                final String signature = object.get("signature").getAsString();
                skins.put(owner, new PlayerSkin(value, signature));
            }

            return List.of(
                    new NPC("Discord", skins.get("Discord"), instance, new Pos(8.5, 15, 8.5),
                            player -> Messenger.info(player, Component.text("Click here to join the Discord server")
                                    .clickEvent(ClickEvent.openUrl("https://minestom.net/discord")))),
                    new NPC("Website", skins.get("Website"), instance, new Pos(-7.5, 15, 8.5),
                            player -> Messenger.info(player, Component.text("Click here to go to the Minestom website")
                                    .clickEvent(ClickEvent.openUrl("https://minestom.net")))),
                    new NPC("GitHub", skins.get("GitHub"), instance, new Pos(8.5, 15, -7.5),
                            player -> Messenger.info(player, Component.text("Click here to go to the Arena GitHub repository")
                                    .clickEvent(ClickEvent.openUrl("https://github.com/Minestom/Arena")))),
                    new NPC("Play", skins.get("Play"), instance, new Pos(-7.5, 15, -7.5), ArenaCommand::open)
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
