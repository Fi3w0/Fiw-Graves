package com.fiw.graves.entity;

import com.fiw.graves.config.Config;
import com.fiw.graves.grave.Grave;
import com.google.common.collect.HashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.UUID;

final class GraveHeads {
	private GraveHeads() {
	}

	static ItemStack resolve(Grave grave) {
		ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
		Config config = Config.get();
		if (!config.headTextureOverride.isBlank()) {
			HashMultimap<String, Property> properties = HashMultimap.create();
			properties.put("textures", new Property("textures", config.headTextureOverride));
			GameProfile profile = new GameProfile(UUID.randomUUID(), grave.ownerName(), new PropertyMap(properties));
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
		} else {
			// Same mechanism vanilla uses for player-head blocks placed with just a UUID -
			// the server resolves the skin asynchronously, works for offline players too.
			stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(grave.owner()));
		}

		return stack;
	}
}
