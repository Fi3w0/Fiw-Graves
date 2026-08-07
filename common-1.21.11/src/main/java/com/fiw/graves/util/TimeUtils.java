package com.fiw.graves.util;

public final class TimeUtils {
	private TimeUtils() {
	}

	public static String formatDuration(long millis) {
		if (millis <= 0) return "0s";

		long totalSeconds = millis / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;

		StringBuilder builder = new StringBuilder();
		if (hours > 0) builder.append(hours).append("h ");
		if (hours > 0 || minutes > 0) builder.append(minutes).append("m ");
		builder.append(seconds).append("s");
		return builder.toString();
	}
}
