package com.fiw.graves.platform;

import java.nio.file.Path;

public interface PlatformHelper {
	Path getConfigDir();

	String getPlatformName();

	boolean isModLoaded(String modId);
}
