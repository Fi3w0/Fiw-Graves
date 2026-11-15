package com.fiw.graves.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Write-then-rename so a crash mid-write can never leave a half-written/corrupt file behind.
 */
public final class FileUtils {
	private FileUtils() {
	}

	public static void writeAtomic(Path target, String content) throws IOException {
		Path parent = target.toAbsolutePath().getParent();
		if (parent != null) Files.createDirectories(parent);

		Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
		Files.writeString(tmp, content, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
