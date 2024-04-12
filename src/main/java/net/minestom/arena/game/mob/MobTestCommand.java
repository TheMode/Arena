package net.minestom.arena.game.mob;

import net.minestom.arena.Items;
import net.minestom.arena.game.Arena;
import net.minestom.arena.game.ArenaManager;
import net.minestom.arena.utils.CommandUtils;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentNumber;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.utils.MathUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class MobTestCommand extends Command {
    public MobTestCommand() {
        super("test");
        setCondition((sender, commandString) -> CommandUtils.arenaOnly(sender, commandString) &&
                sender instanceof Player player && player.getPermissionLevel() == 4);

        final ArgumentLiteral coins = ArgumentType.Literal("coins");
        final ArgumentLiteral stage = ArgumentType.Literal("stage");
        final ArgumentLiteral clear = ArgumentType.Literal("clear");
        final ArgumentLiteral clazz = ArgumentType.Literal("class");
        final ArgumentLiteral spawn = ArgumentType.Literal("spawn");
        final ArgumentLiteral immortal = ArgumentType.Literal("immortal");
        final ArgumentLiteral strong = ArgumentType.Literal("strong");
        final ArgumentLiteral damageme = ArgumentType.Literal("damageme");

        final ArgumentNumber<Integer> coinsAmount = ArgumentType.Integer("amount")
                .between(0, 1000);
        final ArgumentNumber<Integer> classId = ArgumentType.Integer("id")
                .between(0, MobArena.CLASSES.size() - 1);
        final ArgumentNumber<Integer> mobType = ArgumentType.Integer("type")
                .between(0, MobArena.MOB_GENERATORS.size() - 1);

        addSyntax((sender, context) -> ((Player) sender).getInventory().addItemStack(Items.COIN
                .withAmount(context.get(coinsAmount))), coins, coinsAmount);
        addSyntax((sender, context) -> ((Player) sender).getInventory().addItemStack(Items.COIN
                .withAmount(10)), coins);

        addSyntax((sender, context) -> arena(sender)
                .ifPresent(MobArena::nextStage), stage);

        addSyntax((sender, context) -> arena(sender).ifPresent(arena -> {
            for (Entity entity : arena.instance().getEntities()) {
                if (entity instanceof ArenaMob arenaMob)
                    arenaMob.kill();
            }
        }), clear);

        addSyntax((sender, context) -> arena(sender).ifPresent(arena ->
            arena.setPlayerClass(
                    (Player) sender,
                    MobArena.CLASSES.get(MathUtils.clamp(
                            context.get(classId),
                            0, MobArena.CLASSES.size() - 1
                    ))
            )
        ), clazz, classId);

        addSyntax((sender, context) -> arena(sender).ifPresent(arena ->
            MobArena.MOB_GENERATORS.get(MathUtils.clamp(
                    context.get(mobType),
                    0, MobArena.MOB_GENERATORS.size() - 1
            )).generate(new MobGenerationContext(arena)).ifPresent(entity ->
                    entity.setInstance(arena.instance(), ((Player) sender).getPosition()))), spawn, mobType);

        addSyntax((sender, context) -> {
            final Player player = (Player) sender;
            player.setInvulnerable(!player.isInvulnerable());
        }, immortal);

        addSyntax((sender, context) ->
                ((Player) sender).getInventory().addItemStack(ItemStack.builder(Material.COOKED_CHICKEN)
                        .set(MobArena.MELEE_TAG, 10000).build()
        ), strong);

        addSyntax((sender, context) -> ((Player) sender).damage(DamageType.OUT_OF_WORLD, 10), damageme);
    }

    private static @NotNull Optional<MobArena> arena(CommandSender sender) {
        if (!(sender instanceof Player player)) return Optional.empty();

        for (Arena arena : ArenaManager.list()) {
            if (!(arena instanceof MobArena mobArena)) continue;

            if (arena.group().members().contains(player))
                return Optional.of(mobArena);
        }

        return Optional.empty();
    }
}
