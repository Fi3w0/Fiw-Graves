package com.fiw.graves.entity;

import com.fiw.graves.config.Config;
import com.fiw.graves.grave.Grave;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Optional;
import java.util.UUID;

final class GraveHeads {
	private GraveHeads() {
	}

	static ItemStack resolve(Grave grave) {
		ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
		Config config = Config.get();
		if (!config.headTextureOverride.isBlank()) {
			GameProfile profile = new GameProfile(UUID.randomUUID(), grave.ownerName());
			profile.getProperties().put("textures", new Property("textures", config.headTextureOverride));
			stack.set(DataComponents.PROFILE, new ResolvableProfile(profile));
		} else {
			// Same mechanism vanilla uses for player-head blocks placed with just a UUID -
			// the server resolves the skin asynchronously, works for offline players too.
			stack.set(DataComponents.PROFILE, new ResolvableProfile(Optional.empty(), Optional.of(grave.owner()), new PropertyMap()));
		}

		return stack;
	}
}
