package com.fiw.graves.neoforge;

import com.fiw.graves.Constants;
import com.fiw.graves.config.Config;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Constants.MOD_ID)
public class NeoForgeGravesMod {
	public static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_NAME);

	public NeoForgeGravesMod(IEventBus modBus) {
		Config.load();

		NeoForge.EVENT_BUS.register(NeoForgeEventHooks.class);

		LOGGER.info("{} initializing (NeoForge)", Constants.MOD_NAME);
	}
}
