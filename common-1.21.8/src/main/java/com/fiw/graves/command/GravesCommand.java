package com.fiw.graves.command;

import com.fiw.graves.config.Config;
import com.fiw.graves.grave.Grave;
import com.fiw.graves.grave.GraveManager;
import com.fiw.graves.text.Messages;
import com.fiw.graves.util.TimeUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The /graves command tree. Admin-only branches (list-others, tp-others, rollback, reload) gate on
 * vanilla operator level 2 - no permission-node system - so this behaves like any other mod command.
 */
public final class GravesCommand {
	private static final String NAME = "graves";
	private static final String ALIAS = "fiwgraves";

	private GravesCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var node = dispatcher.register(build(NAME));
		dispatcher.register(Commands.literal(ALIAS).redirect(node));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> build(String label) {
		return Commands.literal(label)
				.executes(ctx -> help(ctx, label))
				.then(Commands.literal("help").executes(ctx -> help(ctx, label)))
				.then(Commands.literal("reload")
						.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(GravesCommand::reload))
				.then(Commands.literal("list")
						.executes(ctx -> list(ctx, label, ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.executes(ctx -> list(ctx, label, EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("tp")
						.executes(GravesCommand::teleportLatest)
						.then(Commands.argument("index", IntegerArgumentType.integer(0))
								.executes(GravesCommand::teleportOwnIndex))
						.then(Commands.argument("player", EntityArgument.player())
								.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.argument("index", IntegerArgumentType.integer(0))
										.executes(GravesCommand::teleportOther))))
				.then(Commands.literal("rollback")
						.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> rollback(ctx, 0))
								.then(Commands.argument("index", IntegerArgumentType.integer(0))
										.executes(ctx -> rollback(ctx, IntegerArgumentType.getInteger(ctx, "index"))))))
				.then(Commands.literal("restore")
						.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> restore(ctx, 0))
								.then(Commands.argument("index", IntegerArgumentType.integer(0))
										.executes(ctx -> restore(ctx, IntegerArgumentType.getInteger(ctx, "index"))))));
	}

	private static int help(CommandContext<CommandSourceStack> ctx, String label) {
		for (Component line : Messages.help(label, canTeleport(ctx.getSource()))) {
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return 1;
	}

	/** Player grave-teleport is a config toggle; operators can always teleport. */
	private static boolean canTeleport(CommandSourceStack source) {
		return Config.get().playerGraveTeleport
				|| source.hasPermission(Commands.LEVEL_GAMEMASTERS);
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		try {
			Config.load();
			ctx.getSource().sendSuccess(Messages::reloadOk, true);
		} catch (Exception e) {
			ctx.getSource().sendFailure(Messages.reloadFailed(String.valueOf(e.getMessage())));
		}
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx, String label, ServerPlayer target) {
		CommandSourceStack source = ctx.getSource();
		List<Grave> graves = GraveManager.get(source.getServer()).getGraves(target.getUUID());

		if (graves.isEmpty()) {
			source.sendSuccess(Messages::listEmpty, false);
			return 1;
		}

		source.sendSuccess(() -> Messages.listHeader(target.getGameProfile().getName()), false);
		int despawnSeconds = Config.get().despawnSeconds;
		boolean self = source.getEntity() instanceof ServerPlayer p && p.getUUID().equals(target.getUUID());
		boolean warpLinks = canTeleport(source);

		for (int i = 0; i < graves.size(); i++) {
			Grave grave = graves.get(i);
			Vec3 pos = grave.position();
			String timeLeft = despawnSeconds < 0 ? "no expiry"
					: TimeUtils.formatDuration(Math.max(0, despawnSeconds * 1000L - (System.currentTimeMillis() - grave.spawnedAt())));
			String command = !warpLinks ? null
					: self
					? "/" + label + " tp " + i
					: "/" + label + " tp " + target.getGameProfile().getName() + " " + i;

			int index = i;
			Component line = Messages.listEntry(index, dimensionName(grave.dimension()),
					(int) pos.x, (int) pos.y, (int) pos.z, timeLeft, command);
			source.sendSuccess(() -> line, false);
		}
		return graves.size();
	}

	private static int teleportLatest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		if (!canTeleport(ctx.getSource())) {
			ctx.getSource().sendFailure(Messages.teleportDisabled());
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<Grave> graves = GraveManager.get(ctx.getSource().getServer()).getGraves(player.getUUID());
		if (graves.isEmpty()) {
			ctx.getSource().sendFailure(Messages.noGraves());
			return 0;
		}
		teleportTo(player, graves.stream().max(Comparator.comparingLong(Grave::spawnedAt)).orElseThrow());
		return 1;
	}

	private static int teleportOwnIndex(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		if (!canTeleport(ctx.getSource())) {
			ctx.getSource().sendFailure(Messages.teleportDisabled());
			return 0;
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		return teleportIndexed(ctx, player, player, IntegerArgumentType.getInteger(ctx, "index"));
	}

	private static int teleportOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer requester = ctx.getSource().getPlayerOrException();
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		return teleportIndexed(ctx, requester, target, IntegerArgumentType.getInteger(ctx, "index"));
	}

	private static int teleportIndexed(CommandContext<CommandSourceStack> ctx, ServerPlayer who, ServerPlayer owner, int index) {
		List<Grave> graves = GraveManager.get(ctx.getSource().getServer()).getGraves(owner.getUUID());
		if (index < 0 || index >= graves.size()) {
			ctx.getSource().sendFailure(Messages.noGraves());
			return 0;
		}
		teleportTo(who, graves.get(index));
		return 1;
	}

	private static void teleportTo(ServerPlayer player, Grave grave) {
		ServerLevel level = player.level().getServer().getLevel(grave.dimension());
		if (level == null) return;
		Vec3 pos = grave.position();
		player.teleportTo(level, pos.x, pos.y + 0.5, pos.z, Set.of(), grave.yaw(), 0, false);
	}

	private static int rollback(CommandContext<CommandSourceStack> ctx, int index) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		GraveManager manager = GraveManager.get(source.getServer());

		Grave restored = manager.popRollback(target.getUUID(), index);
		if (restored == null) {
			source.sendFailure(Messages.rollbackNone());
			return 0;
		}

		Grave respawned = new Grave(UUID.randomUUID(), restored.owner(), restored.ownerName(), target.level().dimension(),
				target.position(), target.getYRot(), restored.remainingItems(), restored.experience(), System.currentTimeMillis());
		manager.addGrave(respawned);

		source.sendSuccess(() -> Messages.rollbackRestored(target.getGameProfile().getName()), true);
		return 1;
	}

	private static int restore(CommandContext<CommandSourceStack> ctx, int index) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		GraveManager manager = GraveManager.get(source.getServer());

		Grave backup = manager.getBackup(target.getUUID(), index);
		if (backup == null) {
			source.sendFailure(Messages.restoreNone());
			return 0;
		}

		Grave respawned = new Grave(UUID.randomUUID(), backup.owner(), backup.ownerName(), target.level().dimension(),
				target.position(), target.getYRot(), backup.remainingItems(), backup.experience(), System.currentTimeMillis());
		manager.addGrave(respawned);

		source.sendSuccess(() -> Messages.restoreRestored(target.getGameProfile().getName()), true);
		return 1;
	}

	private static String dimensionName(ResourceKey<Level> dimension) {
		if (dimension.equals(Level.OVERWORLD)) return "Overworld";
		if (dimension.equals(Level.NETHER)) return "The Nether";
		if (dimension.equals(Level.END)) return "The End";
		return dimension.location().toString();
	}
}
