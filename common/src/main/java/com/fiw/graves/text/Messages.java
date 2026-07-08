package com.fiw.graves.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * All player-facing chat text, built directly as vanilla {@link Component}s in code. Server-side
 * text can't rely on client-side translation keys (a vanilla client has no lang entries for us),
 * so everything is composed here with explicit styling rather than loaded from a message file.
 */
public final class Messages {
	/** Signature accent colour (a muted violet), used for the mod's own highlights. */
	public static final int ACCENT = 0xB05CE6;
	private static final int MUTED = 0x8A8A8A;

	private Messages() {
	}

	private static MutableComponent accent(String text) {
		return Component.literal(text).withColor(ACCENT);
	}

	private static MutableComponent gray(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}

	private static MutableComponent muted(String text) {
		return Component.literal(text).withColor(MUTED);
	}

	// --- grave interaction ---

	public static Component notYourGrave() {
		return Component.literal("This grave isn't yours to open.").withStyle(ChatFormatting.RED);
	}

	public static Component graveSpawned(String world, int x, int y, int z) {
		return accent("⚰ ").append(gray("Your things rest at "))
				.append(Component.literal(world + " " + x + ", " + y + ", " + z).withColor(ACCENT))
				.append(gray("."));
	}

	// --- container title ---

	public static Component graveTitle(String ownerName) {
		return accent("⚰ ").append(Component.literal(ownerName + "'s Grave").withColor(ACCENT));
	}

	// --- /graves help ---

	public static Component[] help(String label, boolean teleportAllowed) {
		java.util.List<Component> lines = new java.util.ArrayList<>();
		lines.add(accent("Fiw Graves").append(muted("  — a place for your things when you're gone")));
		lines.add(helpLine(label, "", "show your graves and this help"));
		if (teleportAllowed) {
			lines.add(helpLine(label, "tp [index]", "warp to your latest grave (or the nth one)"));
		}
		lines.add(helpLine(label, "list [player]", "list active graves"));
		lines.add(helpLine(label, "rollback <player> [index]", "restore a player's lost grave"));
		lines.add(helpLine(label, "reload", "reload the config"));
		return lines.toArray(Component[]::new);
	}

	private static MutableComponent helpLine(String label, String args, String desc) {
		MutableComponent cmd = Component.literal("/" + label + (args.isEmpty() ? "" : " " + args)).withColor(ACCENT);
		return muted("  ").append(cmd).append(muted("  " + desc));
	}

	// --- /graves list ---

	public static Component listHeader(String ownerName) {
		return accent("⚰ ").append(gray(ownerName + "'s graves:"));
	}

	public static Component listEmpty() {
		return gray("No active graves.");
	}

	/** @param teleportCommand click-to-warp command, or {@code null} to render without a warp link */
	public static Component listEntry(int index, String world, int x, int y, int z, String timeLeft, String teleportCommand) {
		MutableComponent line = Component.empty();
		if (teleportCommand != null) {
			line.append(Component.literal("[warp]").withColor(ACCENT)
							.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(teleportCommand))))
					.append(Component.literal(" "));
		}
		return line.append(gray("#" + index + " "))
				.append(Component.literal(world + " " + x + ", " + y + ", " + z).withColor(ACCENT))
				.append(muted("  (" + timeLeft + ")"));
	}

	public static Component noGraves() {
		return gray("You don't have any graves right now.");
	}

	public static Component teleportDisabled() {
		return gray("Grave teleporting is disabled here — you'll have to make the walk.");
	}

	// --- /graves rollback ---

	public static Component rollbackNone() {
		return gray("No recoverable graves in that player's history.");
	}

	public static Component rollbackRestored(String ownerName) {
		return accent("⚰ ").append(gray("Restored a grave for "))
				.append(Component.literal(ownerName).withColor(ACCENT)).append(gray("."));
	}

	// --- /graves reload ---

	public static Component reloadOk() {
		return accent("⚰ ").append(gray("Config reloaded."));
	}

	public static Component reloadFailed(String error) {
		return Component.literal("Reload failed: " + error).withStyle(ChatFormatting.RED);
	}
}
