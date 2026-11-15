package com.fiw.graves.entity;

import com.fiw.graves.config.Config;
import com.fiw.graves.grave.Grave;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
			stack.getOrCreateTag().put("SkullOwner", net.minecraft.nbt.NbtUtils.writeGameProfile(new net.minecraft.nbt.CompoundTag(), profile));
		} else {
			// Same mechanism vanilla uses for player-head blocks placed with just a UUID -
			// the server resolves the skin asynchronously, works for offline players too.
			GameProfile profile = new GameProfile(grave.owner(), grave.ownerName());
			stack.getOrCreateTag().put("SkullOwner", net.minecraft.nbt.NbtUtils.writeGameProfile(new net.minecraft.nbt.CompoundTag(), profile));
		}

		return stack;
	}
}
