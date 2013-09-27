package edu.uci.eecs.crowdsafe.common.io.execution;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.TraceDataSourceException;

public class ExecutionTraceDirectory implements ExecutionTraceDataSource {
	private static class FilePatterns {
		final Map<ExecutionTraceStreamType, String> patterns = new EnumMap<ExecutionTraceStreamType, String>(
				ExecutionTraceStreamType.class);

		public FilePatterns() {
			for (ExecutionTraceStreamType streamType : ALL_STREAM_TYPES) {
				patterns.put(streamType, ".*\\." + streamType.id + "\\..*");
			}
		}
	}

	private static final EnumSet<ExecutionTraceStreamType> ALL_STREAM_TYPES = EnumSet
			.allOf(ExecutionTraceStreamType.class);

	private static final FilePatterns FILE_PATTERNS = new FilePatterns();

	private final int processId;
	private final String processName;
	private final Map<ExecutionTraceStreamType, File> files = new EnumMap<ExecutionTraceStreamType, File>(
			ExecutionTraceStreamType.class);

	public ExecutionTraceDirectory(File dir) throws TraceDataSourceException {
		this(dir, ALL_STREAM_TYPES);
	}

	public ExecutionTraceDirectory(File dir, Set<ExecutionTraceStreamType> streamTypes) throws TraceDataSourceException {
		if (!(dir.exists() && dir.isDirectory())) {
			if (dir.isDirectory())
				throw new IllegalArgumentException(String.format("Source directory %s does not exist!",
						dir.getAbsolutePath()));
			else
				throw new IllegalArgumentException(String.format("Source path %s is not a directory!",
						dir.getAbsolutePath()));
		}

		for (File file : dir.listFiles()) {
			if (file.isDirectory())
				continue;

			for (ExecutionTraceStreamType streamType : streamTypes) {
				if (Pattern.matches(FILE_PATTERNS.patterns.get(streamType), file.getName())) {
					if (files.containsKey(streamType))
						throw new TraceDataSourceException(String.format(
								"Directory %s contains multiple files of type %s: %s and %s", dir.getAbsolutePath(),
								streamType, file.getName(), files.get(streamType).getName()));
					files.put(streamType, file);
				}
			}
		}

		if (files.size() != streamTypes.size()) {
			Set<ExecutionTraceStreamType> requiredTypes = EnumSet.copyOf(streamTypes);
			requiredTypes.removeAll(files.keySet());
			throw new TraceDataSourceException(String.format("Required data files are missing from directory %s: %s",
					dir.getAbsolutePath(), requiredTypes));
		}

		ExecutionTraceStreamType anyType = files.keySet().iterator().next();
		String runSignature = files.get(anyType).getName();
		processName = runSignature.substring(0, runSignature.indexOf(anyType.id) - 1).replace('.', '-');
		runSignature = runSignature.substring(runSignature.indexOf('.', runSignature.indexOf(anyType.id)));

		int lastDash = runSignature.lastIndexOf('-');
		int lastDot = runSignature.lastIndexOf('.');

		processId = Integer.parseInt(runSignature.substring(lastDash + 1, lastDot));
	}

	@Override
	public int getProcessId() {
		return processId;
	}

	@Override
	public String getProcessName() {
		return processName;
	}

	@Override
	public InputStream getDataInputStream(ExecutionTraceStreamType streamType) throws IOException {
		return new FileInputStream(files.get(streamType));
	}

	@Override
	public LittleEndianInputStream getLittleEndianInputStream(ExecutionTraceStreamType streamType) throws IOException {
		File file = files.get(streamType);
		return new LittleEndianInputStream(new FileInputStream(file), "file:" + file.getAbsolutePath());
	}

	@Override
	public String toString() {
		return processName + "-" + processId;
	}
}
