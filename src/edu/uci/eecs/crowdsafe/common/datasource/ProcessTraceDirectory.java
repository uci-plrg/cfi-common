package edu.uci.eecs.crowdsafe.common.datasource;

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

public class ProcessTraceDirectory implements ProcessTraceDataSource {
	private static class FilePatterns {
		final Map<ProcessTraceStreamType, String> patterns = new EnumMap<ProcessTraceStreamType, String>(
				ProcessTraceStreamType.class);

		public FilePatterns() {
			for (ProcessTraceStreamType streamType : ALL_STREAM_TYPES) {
				patterns.put(streamType, ".*\\." + streamType.id + "\\..*");
			}
		}
	}

	private static final EnumSet<ProcessTraceStreamType> ALL_STREAM_TYPES = EnumSet.allOf(ProcessTraceStreamType.class);

	private static final FilePatterns FILE_PATTERNS = new FilePatterns();

	private final int processId;
	private final String processName;
	private final Map<ProcessTraceStreamType, File> files = new EnumMap<ProcessTraceStreamType, File>(
			ProcessTraceStreamType.class);

	public ProcessTraceDirectory(File dir) throws ProcessTraceDataSourceException {
		this(dir, ALL_STREAM_TYPES);
	}

	public ProcessTraceDirectory(File dir, Set<ProcessTraceStreamType> streamTypes)
			throws ProcessTraceDataSourceException {
		for (File file : dir.listFiles()) {
			if (file.isDirectory())
				continue;

			for (ProcessTraceStreamType streamType : streamTypes) {
				if (Pattern.matches(FILE_PATTERNS.patterns.get(streamType), file.getName())) {
					if (files.containsKey(streamType))
						throw new ProcessTraceDataSourceException(String.format(
								"Directory %s contains multiple files of type %s: %s and %s", dir.getAbsolutePath(),
								streamType, file.getName(), files.get(streamType).getName()));
					files.put(streamType, file);
				}
			}
		}

		if (files.size() != streamTypes.size()) {
			Set<ProcessTraceStreamType> requiredTypes = EnumSet.copyOf(streamTypes);
			requiredTypes.removeAll(files.keySet());
			throw new ProcessTraceDataSourceException(String.format(
					"Required data files are missing from directory %s: %s", dir.getAbsolutePath(), requiredTypes));
		}

		ProcessTraceStreamType anyType = files.keySet().iterator().next();
		String runSignature = files.get(anyType).getName();
		processName = runSignature.substring(0, runSignature.indexOf(anyType.id) - 1);
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
	public InputStream getDataInputStream(ProcessTraceStreamType streamType) throws IOException {
		return new FileInputStream(files.get(streamType));
	}

	@Override
	public LittleEndianInputStream getLittleEndianInputStream(ProcessTraceStreamType streamType) throws IOException {
		File file = files.get(streamType);
		return new LittleEndianInputStream(new FileInputStream(file), "file:" + file.getAbsolutePath());
	}

	@Override
	public String toString() {
		return processName + "-" + processId;
	}
}
