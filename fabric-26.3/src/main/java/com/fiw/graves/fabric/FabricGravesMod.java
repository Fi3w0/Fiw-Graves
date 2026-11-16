package com.fiw.graves.fabric;

import com.fiw.graves.Constants;
import com.fiw.graves.command.GravesCommand;
import com.fiw.graves.config.Config;
import com.fiw.graves.entity.FakeEntityTracker;
import com.fiw.graves.grave.GraveService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FabricGravesMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_NAME);

	@Override
	public void onInitialize() {
		Config.load();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> GravesCommand.register(dispatcher));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			GraveService.INSTANCE.tick(server);
			FakeEntityTracker.INSTANCE.tick(server);
		});

		LOGGER.info("{} initializing (Fabric)", Constants.MOD_NAME);
	}
}
