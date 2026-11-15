package com.fiw.graves.neoforge;

import com.fiw.graves.Constants;
import com.fiw.graves.config.Config;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Constants.MOD_ID)
public class NeoForgeGravesMod {
	public static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_NAME);

	public NeoForgeGravesMod() {
		Config.load();

		MinecraftForge.EVENT_BUS.register(NeoForgeEventHooks.class);

		LOGGER.info("{} initializing (NeoForge)", Constants.MOD_NAME);
	}
}
