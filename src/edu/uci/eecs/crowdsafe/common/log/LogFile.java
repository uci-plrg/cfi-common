package edu.uci.eecs.crowdsafe.common.log;

import java.io.File;

public class LogFile {

	public enum CollisionMode {
		OVERWRITE,
		AVOID,
		ERROR
	}

	public enum NoSuchPathMode {
		CREATE,
		SKIP,
		ERROR
	}

	public static class Exception extends RuntimeException {
		Exception(String format, Object... args) {
			super(String.format(format, args));
		}
	}

	public static File create(String filePath, CollisionMode collisionMode, NoSuchPathMode noSuchPathMode) {
		return create(new File(filePath), collisionMode, noSuchPathMode);
	}

	public static File create(File logFile, CollisionMode collisionMode, NoSuchPathMode noSuchPathMode) {
		if (logFile.getParentFile() == null)
			logFile = new File(new File("."), logFile.getPath());

		File directory = logFile.getParentFile();
		if (!(directory.exists() && directory.isDirectory())) {
			switch (noSuchPathMode) {
				case CREATE:
					directory.mkdirs();
					break;
				case SKIP:
					Log.log("Warning: logfile %s requires that directory %s already exists. Skipping this log output.",
							logFile.getName(), directory.getAbsolutePath());
					break;
				case ERROR:
					throw new LogFile.Exception("Logfile %s requires that directory %s already exists",
							logFile.getName(), directory.getAbsolutePath());
			}
		}

		if (logFile.exists()) {
			if (logFile.isDirectory()) {
				throw new LogFile.Exception("logfile %s already exists, and it is a directory!",
						logFile.getAbsolutePath());
			}

			switch (collisionMode) {
				case AVOID:
					String name = logFile.getName();
					int lastDot = name.lastIndexOf('.');
					int index = 0;
					if (lastDot >= 0) {
						String start = name.substring(0, lastDot);
						String end = name.substring(lastDot + 1);
						do {
							logFile = new File(directory, String.format("%s.%d.%s", start, index++, end));
						} while (logFile.exists());
					} else {
						do {
							logFile = new File(directory, String.format("%s.%d", name, index++));
						} while (logFile.exists());
					}
					break;
				case ERROR:
					throw new LogFile.Exception("Logfile %s already exists", logFile.getAbsolutePath());
			}
		}

		return logFile;
	}
}
