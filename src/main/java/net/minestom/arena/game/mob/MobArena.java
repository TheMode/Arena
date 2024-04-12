package net.minestom.arena.game.mob;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.minestom.arena.*;
import net.minestom.arena.feature.Feature;
import net.minestom.arena.feature.Features;
import net.minestom.arena.game.ArenaOption;
import net.minestom.arena.game.Generator;
import net.minestom.arena.game.SingleInstanceArena;
import net.minestom.arena.group.Group;
import net.minestom.arena.lobby.Lobby;
import net.minestom.arena.lobby.LobbySidebarDisplay;
import net.minestom.arena.utils.ItemUtils;
import net.minestom.server.MinecraftServer;
import net.minestom.server.attribute.Attribute;
import net.minestom.server.attribute.AttributeInstance;
import net.minestom.server.attribute.AttributeModifier;
import net.minestom.server.attribute.AttributeOperation;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.projectile.ArrowMeta;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithEntityEvent;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.PickupItemEvent;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.particle.Particle;
import net.minestom.server.particle.ParticleCreator;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.utils.MathUtils;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import net.minestom.server.utils.time.TimeUnit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MobArena implements SingleInstanceArena {
    static final ArenaOption DOUBLE_COINS_OPTION = new ArenaOption(
            "Double Coins", "Double the coins, double the fun",
            NamedTextColor.GOLD, Material.SUNFLOWER);
    static final ArenaOption TOUGH_MOBS_OPTION = new ArenaOption(
            "Tough Mobs", "Makes mobs a lot tougher to beat",
            NamedTextColor.RED, Material.RED_DYE);
    static final ArenaOption MAYHEM_OPTION = new ArenaOption(
            "Mayhem", "A lot more mobs spawn and all your attacks become explosive",
            NamedTextColor.DARK_RED, Material.TNT);
    public static final List<ArenaOption> OPTIONS = List.of(DOUBLE_COINS_OPTION, TOUGH_MOBS_OPTION, MAYHEM_OPTION);

    static final Tag<Integer> MELEE_TAG = Tag.Integer("melee").defaultValue(-10);
    static final Tag<Integer> ARMOR_TAG = Tag.Integer("armor").defaultValue(0);
    private static final AttributeModifier ATTACK_SPEED_MODIFIER = new AttributeModifier("mobarena-attack-speed", 100f, AttributeOperation.ADDITION);

    private static final ItemStack WAND = ItemUtils.stripItalics(ItemStack.builder(Material.BLAZE_ROD)
            .displayName(Component.text("Wand"))
            .build());

    private static final ArenaClass KNIGHT_CLASS = new ArenaClass("Knight", "Starter class with mediocre attack and defense.",
            Icons.SWORD, TextColor.color(0xbebebe), Material.STONE_SWORD,
            new Kit(List.of(ItemStack.of(Material.STONE_SWORD).withTag(MELEE_TAG, 2)),
                    Map.of(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.CHAINMAIL_CHESTPLATE).withTag(ARMOR_TAG, 4))),
            5);
    private static final ArenaClass ARCHER_CLASS = new ArenaClass("Archer", "Easily deal (and take) high damage using your bow.",
            Icons.BOW, TextColor.color(0xf9ff87), Material.BOW,
            new Kit(List.of(ItemStack.of(Material.BOW), ItemStack.of(Material.ARROW)),
                    Map.of(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.LEATHER_CHESTPLATE).withTag(ARMOR_TAG, 3))),
            10);
    public static final List<ArenaClass> CLASSES = List.of(
            KNIGHT_CLASS,
            ARCHER_CLASS,
            new ArenaClass("Tank", "Very beefy, helps your teammates safely deal damage.",
                    Icons.SHIELD, TextColor.color(0x6b8ebe), Material.IRON_CHESTPLATE,
                    new Kit(List.of(ItemStack.of(Material.WOODEN_SWORD).withTag(MELEE_TAG, 1)),
                            Map.of(EquipmentSlot.HELMET, ItemStack.of(Material.CHAINMAIL_HELMET).withTag(ARMOR_TAG, 2),
                                    EquipmentSlot.CHESTPLATE, ItemStack.of(Material.IRON_CHESTPLATE).withTag(ARMOR_TAG, 4),
                                    EquipmentSlot.LEGGINGS, ItemStack.of(Material.CHAINMAIL_LEGGINGS).withTag(ARMOR_TAG, 3),
                                    EquipmentSlot.BOOTS, ItemStack.of(Material.IRON_BOOTS).withTag(ARMOR_TAG, 1))),
                    15),
            new ArenaClass("Mage", "Fight enemies from far away using your long ranged magic missiles.",
                    Icons.POTION, TextColor.color(0x3cbea5), Material.BLAZE_ROD,
                    new Kit(List.of(WAND),
                            Map.of(EquipmentSlot.LEGGINGS, ItemStack.of(Material.LEATHER_LEGGINGS).withTag(ARMOR_TAG, 2))),
                    20),
            new ArenaClass("Berserker", "For when knight doesn't deal enough damage.",
                    Icons.AXE, TextColor.color(0xbe6464), Material.STONE_AXE,
                    new Kit(List.of(ItemStack.of(Material.STONE_AXE).withTag(MELEE_TAG, 5)),
                            Map.of(EquipmentSlot.BOOTS, ItemStack.of(Material.GOLDEN_BOOTS).withTag(ARMOR_TAG, 2))),
                    25)
    );

    private static final ArenaUpgrade ALLOYING_UPGRADE = new ArenaUpgrade(
            "Alloying", "Increase armor effectiveness by 15%",
            TextColor.color(0xf9ff87), Material.LAVA_BUCKET, null, null,
            level -> "Armor effectiveness is currently increased by " + MathUtils.round(Math.pow(1.15, level) * 100 - 100, 2) + "%",
            10, 1.1f, 20);
    private static final UUID HEALTHCARE_UUID = new UUID(9354678, 3425896);
    private static final UUID COMBAT_TRAINING_UUID = new UUID(24539786, 23945687);

    public static final List<ArenaUpgrade> UPGRADES = List.of(
            new ArenaUpgrade("Improved Healthcare", "Increases max health by two hearts per level, and healing by one heart if you have the upgrade.",
                    TextColor.color(0x63ff52), Material.POTION, (player, count) -> {
                        final AttributeModifier modifier = new AttributeModifier(
                                HEALTHCARE_UUID, "mobarena-healthcare", 4 * count,
                                AttributeOperation.ADDITION
                        );

                        player.getAttribute(Attribute.MAX_HEALTH).removeModifier(modifier);
                        player.getAttribute(Attribute.MAX_HEALTH).addModifier(modifier);
                    }, player -> {
                        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
                        for (AttributeModifier modifier : attribute.getModifiers()) {
                            if (!modifier.getId().equals(HEALTHCARE_UUID)) continue;
                            attribute.removeModifier(modifier);
                        }
                        player.heal();
                    }, level -> "Currently gives " + level * 2 + " extra hearts" + (level > 0 ? " and heals an extra heart" : ""),
                    10, 1.3f, 5),
            new ArenaUpgrade("Combat Training", "All physical attacks deal 10% more damage",
                    TextColor.color(0xff5c3c), Material.IRON_SWORD, (player, count) -> {
                        final AttributeModifier modifier = new AttributeModifier(
                                COMBAT_TRAINING_UUID, "mobarena-combat-training", (float) (Math.pow(1.1, count) - 1),
                                AttributeOperation.MULTIPLY_TOTAL
                        );

                        player.getAttribute(Attribute.ATTACK_DAMAGE).removeModifier(modifier);
                        player.getAttribute(Attribute.ATTACK_DAMAGE).addModifier(modifier);
                    }, player -> {
                        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_DAMAGE);
                        for (AttributeModifier modifier : attribute.getModifiers()) {
                            if (!modifier.getId().equals(COMBAT_TRAINING_UUID)) continue;
                            attribute.removeModifier(modifier);
                        }
                    }, level -> "Physical attacks now deal " + MathUtils.round(Math.pow(1.1, level) * 100 - 100, 2) + "% more damage",
                    10, 1.1f, 20),
            ALLOYING_UPGRADE
    );

    private static float percentageClass(@NotNull MobGenerationContext context, @NotNull ArenaClass arenaClass) {
        return context.arena().playerClasses.values().stream()
                .filter(arenaClass::equals).count() / (float) context.group().members().size();
    }

    static final List<Generator<? extends Entity, MobGenerationContext>> MOB_GENERATORS = List.of(
            Generator.builder(ZombieMob::new)
                    .chance(0.5)
                    .build(),
            Generator.builder(SpiderMob::new)
                    .chance(0.33)
                    .condition(ctx -> ctx.stage() >= 2)
                    .controller(Generator.Controller.maxCount(2))
                    .build(),
            Generator.builder(SkeletonMob::new)
                    .chance(0.25)
                    .condition(ctx -> ctx.stage() >= 4)
                    .build(),
            Generator.builder(BlazeMob::new)
                    .chance(0.1)
                    .condition(ctx -> ctx.stage() >= 6)
                    .controller(Generator.Controller.maxCount(2))
                    .preference(ctx -> ctx.group().members().size() >= 2 ? 1 : 0.5) // Prefer a group size of 2 or more
                    .preference(ctx -> 1 - percentageClass(ctx, ARCHER_CLASS) / 2) // Spawn less if more archers
                    .build(),
            Generator.builder(EndermanMob::new)
                    .chance(0.05)
                    .condition(ctx -> ctx.stage() >= 8)
                    .controller(Generator.Controller.maxCount(ctx -> ctx.stage() / 10)) // +1 max every 10 stages
                    .preference(ctx -> ctx.group().members().size() >= 2 ? 1 : 0.5) // Prefer a group size of 2 or more
                    .build(),
            Generator.builder(EvokerMob::new)
                    .chance(0.1)
                    .condition(ctx -> ctx.stage() >= 10)
                    .controller(Generator.Controller.maxCount(ctx -> ctx.stage() / 10)) // +1 max every 10 stages
                    .preference(ctx -> ctx.group().members().size() / 3f) // Prefer a group size of 3 or more
                    .build()
    );

    static final int SPAWN_RADIUS = 10;
    static final int HEIGHT = 16;
    private final AtomicInteger mobCount = new AtomicInteger();
    private boolean stageInProgress;
    private int initialMobCount;
    private volatile boolean isStopping = false;

    private final Group group;
    private final Set<ArenaOption> options;
    private final BossBar bossBar;
    private final Instance arenaInstance = new MobArenaInstance();
    private final Set<Player> continued = new HashSet<>();
    private final Map<Player, ArenaClass> playerClasses = new HashMap<>();
    private final Map<ArenaUpgrade, Integer> upgrades = new HashMap<>();

    private int stage = 0;

    public MobArena(Group group, Set<ArenaOption> options) {
        this.group = group;
        this.options = Set.copyOf(options);

        group.setDisplay(new MobArenaSidebarDisplay(this));

        // Show boss bar
        bossBar = BossBar.bossBar(
                Component.text("Loading..."), 1,
                BossBar.Color.BLUE, BossBar.Overlay.PROGRESS
        );
        group.showBossBar(bossBar);

        // Remove attack indicator
        for (Player member : group.members()) {
            member.getAttribute(Attribute.ATTACK_SPEED).addModifier(ATTACK_SPEED_MODIFIER);
        }

        arenaInstance.eventNode().addListener(EntityDeathEvent.class, event -> {
            if (!(event.getEntity() instanceof ArenaMob mob) || mob instanceof ArenaMinion) return;

            mobCount.decrementAndGet();

            final int mobsLeft = mobCount.get();

            if (mobsLeft > 0) {
                final String mobOrMobs = " mob" + (initialMobCount == 1 ? "" : "s");

                bossBar.name(Component.text("Kill mobs! " + mobsLeft + mobOrMobs + " remaining"));
                bossBar.progress((float) mobsLeft / initialMobCount);
                bossBar.color(BossBar.Color.RED);
            } else {
                onStageCleared();
            }
        }).addListener(PickupItemEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                player.getInventory().addItemStack(event.getItemStack());
            } else {
                // Don't want other mobs to pick up items
                event.setCancelled(true);
            }
        }).addListener(PlayerDeathEvent.class, event -> {
            final Player player = event.getPlayer();

            player.setInstance(Lobby.INSTANCE);

            event.setChatMessage(null);
            Messenger.info(player, "You died. Your last stage was " + stage);
        }).addListener(RemoveEntityFromInstanceEvent.class, event -> {
            // We don't care about entities, only players.
            if (!(event.getEntity() instanceof Player player)) return;

            // Re-add attack indicator
            player.getAttribute(Attribute.ATTACK_SPEED).removeModifier(ATTACK_SPEED_MODIFIER);

            // Remove upgrades
            for (ArenaUpgrade upgrade : upgrades.keySet()) {
                if (upgrade.remove() == null) continue;
                upgrade.remove().accept(player);
            }

            // Hide boss bar
            player.hideBossBar(bossBar);

            // Update scoreboard (for death state)
            group.display().update();
        }).addListener(PlayerEntityInteractEvent.class, event -> {
            Player player = event.getPlayer();
            Entity target = event.getTarget();

            if (!(target instanceof NextStageNPC)) return;
            if (!hasContinued(player)) {
                player.openInventory(new NextStageInventory(player, this));
                player.playSound(Sound.sound(SoundEvent.ENTITY_VILLAGER_YES, Sound.Source.NEUTRAL, 1, 1), target);
            } else {
                Messenger.warn(player, "You already continued");
                player.playSound(Sound.sound(SoundEvent.ENTITY_VILLAGER_NO, Sound.Source.NEUTRAL, 1, 1), target);
            }
        }).addListener(InventoryPreClickEvent.class, event -> {
            final int slot = event.getSlot();
            final ItemStack clickedItem = event.getClickedItem();
            final ItemStack cursorItem = event.getCursorItem();

            if (!(slot >= PlayerInventoryUtils.HELMET_SLOT && slot <= PlayerInventoryUtils.BOOTS_SLOT))
                return;

            if (clickedItem.getTag(Kit.KIT_ITEM_TAG) || cursorItem.getTag(Kit.KIT_ITEM_TAG)) {
                event.setCancelled(true);
            }
        });
    }

    @Override
    public void start() {
        nextStage();
    }

    @Override
    public void stop() {
        if (isStopping) return;
        isStopping = true;

        if (stageInProgress && arenaInstance.getPlayers().size() > 0) {
            final Duration time = Duration.ofSeconds(30);
            final long timeoutAt = System.currentTimeMillis() + time.toMillis();
            Messenger.warn(group(), "This arena is stopping. You have " + time.getSeconds() + " seconds to complete the stage");

            //TODO: Use Messenger to provide nice countdowns
            MinecraftServer.getSchedulerManager().submitTask(() -> {
                if (stageInProgress && arenaInstance.getPlayers().size() > 0
                        && System.currentTimeMillis() < timeoutAt)
                    return TaskSchedule.duration(Duration.ofSeconds(1));

                for (Player player : arenaInstance.getPlayers()) {
                    player.setInstance(Lobby.INSTANCE);
                    Messenger.info(player, "You left the arena. Your last stage was " + stage);
                }

                group.setDisplay(new LobbySidebarDisplay(group));
                unregister();

                return TaskSchedule.stop();
            });
        } else {
            group.setDisplay(new LobbySidebarDisplay(group));
            unregister();
        }
    }

    private void onStageCleared() {
        if (!stageInProgress) return;
        setStageInProgress(false);

        // Revive dead players
        for (Player player : deadPlayers()) {
            player.setInstance(arenaInstance, spawnPosition(player));
            player.getAttribute(Attribute.ATTACK_SPEED).addModifier(ATTACK_SPEED_MODIFIER);
            player.showBossBar(bossBar);
            playerClass(player).apply(player);
        }

        final TextComponent.Builder builder = Component.text()
                .append(Component.newline())
                .append(Component.text("Coins", Messenger.ORANGE_COLOR, TextDecoration.BOLD))
                .append(Component.newline());
        for (Player member : group.members()) {
            member.getInventory().addItemStack(Items.COIN.withAmount(
                    (int) Math.ceil(initialMobCount / (double) group.members().size()) * (hasOption(DOUBLE_COINS_OPTION) ? 2 : 1)));
            final int coins = Arrays.stream(member.getInventory().getItemStacks())
                    .filter(item -> item.isSimilar(Items.COIN))
                    .mapToInt(ItemStack::amount)
                    .sum();

            builder.append(Component.text(member.getUsername(), NamedTextColor.GRAY))
                    .append(Component.text(" | ", Messenger.PINK_COLOR))
                    .append(Component.text(coins + " coin" + (coins == 1 ? "" : "s"), NamedTextColor.GRAY))
                    .append(Component.newline());
        }
        group.sendMessage(builder);

        for (Map.Entry<ArenaUpgrade, Integer> entry : upgrades.entrySet()) {
            if (entry.getKey().apply() != null)
                for (Player player : arenaInstance.getPlayers()) {
                    entry.getKey().apply().accept(player, entry.getValue());
                }
        }

        final int playerCount = arenaInstance.getPlayers().size();
        final String playerOrPlayers = "player" + (playerCount == 1 ? "" : "s");

        bossBar.name(Component.text("Stage cleared! Waiting for " + playerCount + " more " + playerOrPlayers + " to continue"));
        bossBar.progress(0);
        bossBar.color(BossBar.Color.GREEN);

        group().playSound(Sound.sound(SoundEvent.UI_TOAST_CHALLENGE_COMPLETE, Sound.Source.MASTER, 0.5f, 1), Sound.Emitter.self());
        Messenger.info(group(), "Stage " + stage + " cleared! Talk to the NPC to continue to the next stage");
        new NextStageNPC().setInstance(arenaInstance, new Pos(0.5, HEIGHT, 0.5));

        // Update scoreboard (for death state)
        group.display().update();
    }

    public void continueToNextStage(Player player) {
        if (!continued.add(player)) return;

        group.display().update();

        final int continuedCount = continued.size();
        final int haveToContinue = arenaInstance.getPlayers().size();
        final int untilStart = haveToContinue - continuedCount;

        if (untilStart <= 0) {
            Messenger.info(group(), player.getUsername() + " has continued. Starting the next wave.");

            bossBar.name(Component.text("Wave starting..."));
            bossBar.progress(1);
            bossBar.color(BossBar.Color.BLUE);

            Messenger.countdown(group(), 3)
                    .thenRun(this::nextStage)
                    .thenRun(continued::clear);
        } else {
            final String playerOrPlayers = "player" + (untilStart == 1 ? "" : "s");

            Messenger.info(group(), player.getUsername() + " has continued. " + untilStart + " more " + playerOrPlayers + " must continue to start the next wave.");

            bossBar.name(Component.text("Stage cleared! Waiting for " + untilStart + " more " + playerOrPlayers + " to continue"));
            bossBar.progress((float) continuedCount / haveToContinue);
            bossBar.color(BossBar.Color.GREEN);
        }
    }

    public int stage() {
        return stage;
    }

    public boolean stageInProgress() {
        return stageInProgress;
    }

    void setStageInProgress(boolean stageInProgress) {
        this.stageInProgress = stageInProgress;
        group.display().update();
    }

    public void nextStage() {
        setStageInProgress(true);
        stage++;
        initialMobCount = Math.min((int) (stage * 1.5) * (hasOption(MAYHEM_OPTION) ? 10 : 1), 200);
        for (Entity entity : arenaInstance.getEntities()) {
            if (entity instanceof NextStageNPC) {
                entity.remove();
                break;
            }
        }

        for (Player member : group.members()) {
            member.setHealth(member.getHealth() + (getUpgrade(UPGRADES.get(0)) > 0 ? 6 : 4)); // Heal 2 hearts + 1 heart if you have improved healthcare
            playerClass(member).apply(member);
        }

        for (Entity entity : Generator.generateAll(MOB_GENERATORS, initialMobCount, () -> new MobGenerationContext(this))) {
            entity.setInstance(arenaInstance, Vec.ONE
                    .rotateAroundY(ThreadLocalRandom.current().nextDouble(2 * Math.PI))
                    .mul(SPAWN_RADIUS, 0, SPAWN_RADIUS)
                    .asPosition()
                    .add(0, HEIGHT, 0));
        }

        final String mobOrMobs = " mob" + (initialMobCount == 1 ? "" : "s");

        arenaInstance.showTitle(Title.title(
                Component.text("Stage " + stage, NamedTextColor.GREEN),
                Component.text(initialMobCount + mobOrMobs)
        ));

        arenaInstance.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 2f));
        Messenger.info(arenaInstance, "Stage " + stage + " has begun! Kill all the mobs to proceed to the next stage");

        bossBar.name(Component.text("Kill mobs! " + initialMobCount + mobOrMobs + " remaining"));
        bossBar.progress(1f);
        bossBar.color(BossBar.Color.RED);

        mobCount.set(initialMobCount);
    }

    public boolean hasContinued(Player player) {
        return continued.contains(player);
    }

    public ArenaClass playerClass(Player player) {
        return playerClasses.getOrDefault(player, KNIGHT_CLASS); // Knight class is default
    }

    public void setPlayerClass(Player player, ArenaClass arenaClass) {
        playerClasses.put(player, arenaClass);
        arenaClass.apply(player);
        group.display().update();
    }

    public int getUpgrade(ArenaUpgrade upgrade) {
        return upgrades.getOrDefault(upgrade, 0);
    }

    public void addUpgrade(ArenaUpgrade upgrade) {
        final int level = getUpgrade(upgrade) + 1;
        upgrades.put(upgrade, level);
        if (upgrade.apply() != null)
            for (Player player : arenaInstance.getPlayers()) {
                upgrade.apply().accept(player, level);
            }
    }

    private Set<Player> deadPlayers() {
        Set<Player> deadPlayers = new HashSet<>(group.members());
        deadPlayers.removeAll(arenaInstance.getPlayers());

        return deadPlayers;
    }

    @Contract("null -> false")
    public boolean hasOption(@Nullable ArenaOption option) {
        return options.contains(option);
    }

    public @NotNull Group group() {
        return group;
    }

    @Override
    public @NotNull Instance instance() {
        return arenaInstance;
    }

    @Override
    public @NotNull Pos spawnPosition(@NotNull Player player) {
        return new Pos(0.5, HEIGHT, 0.5);
    }

    @Override
    public @NotNull List<Feature> features() {
        final List<Feature> features = new ArrayList<>(List.of(Features.bow((entity, power) -> {
            final EntityProjectile projectile = new EntityProjectile(entity, EntityType.ARROW);
            final ArrowMeta meta = (ArrowMeta) projectile.getEntityMeta();
            meta.setCritical(power >= 0.9);
            projectile.scheduleRemove(Duration.of(100, TimeUnit.SERVER_TICK));

            return projectile;
        }), Features.combat(false, (attacker, victim) -> {
            float damage = 1;
            if (attacker instanceof LivingEntity livingEntity) {
                damage = livingEntity.getAttributeValue(Attribute.ATTACK_DAMAGE);
            } else if (attacker instanceof EntityProjectile projectile && projectile.getShooter() instanceof Player player) {
                final float movementSpeed = (float) (projectile.getVelocity().length() / MinecraftServer.TICK_PER_SECOND);
                damage = movementSpeed * player.getAttributeValue(Attribute.ATTACK_DAMAGE);
            }

            if (attacker instanceof Player player) {
                final ItemStack item = player.getItemInMainHand();
                final int tier = item.isAir() ? 0 : item.getTag(MELEE_TAG);
                final float multi = 0.1f * tier; // no weapon = 0 tier, non damaging item = -10 tier = 0 damage

                damage *= 1 + multi;
            }

            if (victim instanceof Player player) {
                int armorPoints = player.getHelmet().getTag(ARMOR_TAG) +
                        player.getChestplate().getTag(ARMOR_TAG) +
                        player.getLeggings().getTag(ARMOR_TAG) +
                        player.getBoots().getTag(ARMOR_TAG);

                // Armor point = 4% damage reduction
                // 20 armor points = max reduction
                final float multi = (float) (-0.04f * armorPoints * Math.pow(1.15, getUpgrade(ALLOYING_UPGRADE)));

                damage *= Math.max(1 + multi, 0.2);
            }

            return damage;
        }, victim -> {
            if (victim instanceof Player) return 500;
            else return 100;
        }), Features.drop(item ->
                !item.getTag(Kit.KIT_ITEM_TAG)
        ), Features.functionalItem(
                // Normally you'd use a.isSimilar(b) but the tags are very much different on these items
                item -> WAND.material() == item.material() && WAND.getDisplayName().equals(item.getDisplayName()),
                player -> {
                    final Instance instance = player.getInstance();
                    final AtomicReference<Pos> atomicPos = new AtomicReference<>(player.getPosition().add(0, player.getEyeHeight(), 0));
                    final AtomicInteger atomicAge = new AtomicInteger();

                    MinecraftServer.getSchedulerManager().submitTask(() -> {
                        if (instance == null) return TaskSchedule.stop();

                        final Pos playerEyes = player.getPosition().add(0, player.getEyeHeight(), 0);
                        final Pos pos = atomicPos.getAndUpdate(p -> p.withLookAt(playerEyes.add(playerEyes.direction().mul(30)))
                                .add(p.direction()));
                        final int age = atomicAge.getAndIncrement();

                        if (instance.getNearbyEntities(pos, 2)
                                .stream()
                                .anyMatch(entity -> entity instanceof ArenaMob) ||
                                !instance.getBlock(pos).isAir() ||
                                !instance.getWorldBorder().isInside(pos) ||
                                age >= 30) {

                            explosion(DamageType.MOB_ATTACK, instance, pos, 5, 0.5f, 7, 1);

                            return TaskSchedule.stop();
                        }

                        instance.sendGroupedPacket(ParticleCreator.createParticlePacket(
                                Particle.FIREWORK, true, pos.x(), pos.y(), pos.z(),
                                0.3f, 0.3f, 0.3f, 0.01f, 50, null
                        ));
                        instance.playSound(
                                Sound.sound(SoundEvent.ENTITY_AXOLOTL_SWIM, Sound.Source.NEUTRAL, 1, 1),
                                pos.x(), pos.y(), pos.z()
                        );

                        return TaskSchedule.tick(1);
                    });
                }, 1500)
        ));

        if (hasOption(MAYHEM_OPTION)) {
            features.add(node -> node.addListener(ProjectileCollideWithEntityEvent.class, event -> {
                if (!(event.getEntity() instanceof EntityProjectile projectile)) return;
                if (!(projectile.getShooter() instanceof Player shooter)) return;
                if (!(event.getTarget() instanceof LivingEntity target)) return;

                final Instance instance = event.getInstance();
                final Pos pos = target.getPosition();
                explosion(DamageType.MOB_PROJECTILE, instance, pos, 6, 1, 7, 0.3f);
            }).addListener(EntityAttackEvent.class, event -> {
                if (!(event.getEntity() instanceof Player player)) return;
                if (!(event.getTarget() instanceof LivingEntity target)) return;

                final Instance instance = event.getInstance();
                final Pos pos = target.getPosition();
                explosion(DamageType.MOB_ATTACK, instance, pos, 3, 1f, 3, 0.3f);
            }));
        }

        return features;
    }

    private static void explosion(DamageType damageType, Instance instance, Pos pos, int range, float offset, int damage, float volume) {
        instance.sendGroupedPacket(ParticleCreator.createParticlePacket(
                Particle.EXPLOSION, pos.x(), pos.y(), pos.z(),
                offset, offset, offset, 5
        ));
        instance.playSound(
                Sound.sound(SoundEvent.ENTITY_GENERIC_EXPLODE, Sound.Source.NEUTRAL, volume, 1),
                pos.x(), pos.y(), pos.z()
        );
        for (Entity entity : instance.getNearbyEntities(pos, range)) {
            if (entity instanceof LivingEntity livingEntity && !(entity instanceof Player))
                livingEntity.damage(damageType, damage);
        }
    }
}
