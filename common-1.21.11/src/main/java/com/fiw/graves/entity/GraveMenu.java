package com.fiw.graves.entity;

import com.fiw.graves.grave.Grave;
import com.fiw.graves.text.Messages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

public final class GraveMenu {
	private GraveMenu() {
	}

	public static void open(ServerPlayer player, Grave grave) {
		// Pull any stacks beyond slot 54 down into the slots the chest UI can actually show.
		grave.compactIfOverflowing();

		MenuType<?> menuType = switch (grave.rows()) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};

		MenuProvider provider = new SimpleMenuProvider(
				(containerId, playerInventory, p) -> new ChestMenu(menuType, containerId, playerInventory, grave.container(), grave.rows()),
				Messages.graveTitle(grave.ownerName())
		);

		player.openMenu(provider);
	}
}
