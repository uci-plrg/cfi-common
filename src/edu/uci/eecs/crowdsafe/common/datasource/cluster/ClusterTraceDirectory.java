package edu.uci.eecs.crowdsafe.common.datasource.cluster;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.datasource.TraceDataSourceException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ClusterTraceDirectory implements ClusterTraceDataSource {

	private static final EnumSet<ClusterTraceStreamType> ALL_STREAM_TYPES = EnumSet.allOf(ClusterTraceStreamType.class);

	private final Map<AutonomousSoftwareDistribution, Map<ClusterTraceStreamType, File>> filesByCluster = new HashMap<AutonomousSoftwareDistribution, Map<ClusterTraceStreamType, File>>();

	private final File directory;

	public ClusterTraceDirectory(File dir) throws TraceDataSourceException {
		this(dir, ALL_STREAM_TYPES);
	}

	public ClusterTraceDirectory(File directory, Set<ClusterTraceStreamType> streamTypes)
			throws TraceDataSourceException {
		this.directory = directory;
		File[] ls = directory.listFiles();
		for (AutonomousSoftwareDistribution cluster : ConfiguredSoftwareDistributions.getInstance().distributions
				.values()) {

			Map<ClusterTraceStreamType, File> files = null;

			for (File file : ls) {
				if (file.isDirectory())
					continue;

				for (ClusterTraceStreamType streamType : streamTypes) {
					if (matches(file.getName(), cluster, streamType)) {
						if (files == null) {
							files = new EnumMap<ClusterTraceStreamType, File>(ClusterTraceStreamType.class);
							filesByCluster.put(cluster, files);
						}
						if (files.containsKey(streamType))
							throw new TraceDataSourceException(String.format(
									"Directory %s contains multiple files of type %s for cluster %s: %s and %s",
									directory.getAbsolutePath(), streamType, cluster.name, file.getName(),
									files.get(streamType).getName()));
						files.put(streamType, file);
					}
				}
			}
			
			if ((files != null) && (files.size() < ALL_STREAM_TYPES.size())) {
				Set<ClusterTraceStreamType> requiredTypes = EnumSet.copyOf(streamTypes);
				requiredTypes.removeAll(files.keySet());
				Log.log("Directory %s contains some but not all files for cluster %s.\n\tMissing types are %s.\n\tSkipping this cluster.",
						directory.getAbsolutePath(), cluster.name, requiredTypes);
				filesByCluster.remove(cluster);
			}
		}
	}

	private boolean matches(String filename, AutonomousSoftwareDistribution cluster, ClusterTraceStreamType streamType) {
		return matches(filename, ".*", cluster, streamType);
	}

	private boolean matches(String filename, String processName, AutonomousSoftwareDistribution cluster,
			ClusterTraceStreamType streamType) {
		return Pattern.matches(
				String.format("%s\\.%s\\.%s\\.%s", processName, cluster.name, streamType.id, streamType.extension),
				filename);
	}

	@Override
	public InputStream getDataInputStream(AutonomousSoftwareDistribution cluster, ClusterTraceStreamType streamType)
			throws IOException {
		return new FileInputStream(filesByCluster.get(cluster).get(streamType));
	}

	@Override
	public LittleEndianInputStream getLittleEndianInputStream(AutonomousSoftwareDistribution cluster,
			ClusterTraceStreamType streamType) throws IOException {
		File file = filesByCluster.get(cluster).get(streamType);
		return new LittleEndianInputStream(new FileInputStream(file), "file:" + file.getAbsolutePath());
	}

	@Override
	public Collection<AutonomousSoftwareDistribution> getReprsentedClusters() {
		return Collections.unmodifiableSet(filesByCluster.keySet());
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ": " + directory.getAbsolutePath();
	}
}
