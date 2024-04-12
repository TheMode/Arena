package net.minestom.arena.game.mob;

import net.minestom.server.attribute.Attribute;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.ai.GoalSelector;
import net.minestom.server.entity.ai.goal.MeleeAttackGoal;
import net.minestom.server.entity.ai.target.ClosestEntityTarget;
import net.minestom.server.entity.metadata.monster.raider.EvokerMeta;
import net.minestom.server.entity.metadata.monster.raider.SpellcasterIllagerMeta;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.timer.TaskSchedule;
import net.minestom.server.utils.time.Cooldown;
import net.minestom.server.utils.time.TimeUnit;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

final class EvokerMob extends ArenaMob {
    public EvokerMob(MobGenerationContext context) {
        super(EntityType.EVOKER, context);
        addAIGroup(
                List.of(new ActionGoal(this, Duration.ofSeconds(10), target -> {
                    lookAt(target);

                    ((EvokerMeta) getEntityMeta()).setSpell(SpellcasterIllagerMeta.Spell.SUMMON_VEX);

                    scheduler().scheduleTask(() -> {
                        final Random random = ThreadLocalRandom.current();

                        for (int i = 0; i < random.nextInt(1, 3); i++) {
                            ArenaMob silverfish = new ArenaMinion(EntityType.SILVERFISH, this);
                            silverfish.addAIGroup(
                                    List.of(new MeleeAttackGoal(silverfish, 1.2, 20, TimeUnit.SERVER_TICK)),
                                    List.of(new ClosestEntityTarget(silverfish, 32, entity -> entity instanceof Player))
                            );
                            silverfish.getAttribute(Attribute.MAX_HEALTH).setBaseValue(silverfish.getMaxHealth() / 4);
                            silverfish.heal();
                            final Pos pos = position.add(
                                    random.nextFloat(-2, 2), 0,
                                    random.nextFloat(-2, 2)
                            );
                            silverfish.setInstance(instance, pos);

                            instance.sendGroupedPacket(new ParticlePacket(Particle.POOF, true, pos.x(), pos.y(), pos.z(),
                                    0.2f, 0.2f, 0.2f, 0.1f, 10));
                        }

                        ((EvokerMeta) getEntityMeta()).setSpell(SpellcasterIllagerMeta.Spell.NONE);
                    }, TaskSchedule.seconds(2), TaskSchedule.stop());
                })),
                List.of(new ClosestEntityTarget(this, 32, entity -> entity instanceof Player))
        );
    }

    private static final class ActionGoal extends GoalSelector {
        private final Duration cooldown;
        private final Consumer<Entity> consumer;
        private long lastSummon;
        private Entity target;

        public ActionGoal(@NotNull EntityCreature entityCreature, @NotNull Duration cooldown, Consumer<Entity> consumer) {
            super(entityCreature);
            this.cooldown = cooldown;
            this.consumer = consumer;
        }

        @Override
        public boolean shouldStart() {
            Entity target = entityCreature.getTarget();
            if (target == null || target.getInstance() != entityCreature.getInstance()) target = findTarget();
            if (target == null) return false;
            if (Cooldown.hasCooldown(System.currentTimeMillis(), lastSummon, cooldown)) return false;
            this.target = target;
            return true;
        }

        @Override
        public void start() {
            if (target == null) return;
            entityCreature.setTarget(target);
        }

        @Override
        public void tick(long time) {
            if (!Cooldown.hasCooldown(time, lastSummon, cooldown) && entityCreature.getTarget() != null) {
                lastSummon = time;
                consumer.accept(entityCreature.getTarget());
            }
        }

        @Override
        public boolean shouldEnd() {
            final Entity target = entityCreature.getTarget();
            return target == null || target.isRemoved() ||
                    Cooldown.hasCooldown(System.currentTimeMillis(), lastSummon, cooldown);
        }

        @Override
        public void end() {}
    }
}
