package com.fiw.graves.neoforge.platform;

import com.fiw.graves.platform.PlatformHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public class NeoForgePlatformHelper implements PlatformHelper {
	@Override
	public Path getConfigDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public String getPlatformName() {
		return "NeoForge";
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}
}
