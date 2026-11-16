package com.fiw.graves.platform;

import java.util.ServiceLoader;

public final class Services {
	public static final PlatformHelper PLATFORM = load(PlatformHelper.class);

	private Services() {
	}

	public static <T> T load(Class<T> clazz) {
		return ServiceLoader.load(clazz).findFirst()
				.orElseThrow(() -> new IllegalStateException("Failed to load service for " + clazz.getName()));
	}
}
