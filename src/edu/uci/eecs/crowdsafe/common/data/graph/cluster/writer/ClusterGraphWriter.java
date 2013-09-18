package edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModule;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModuleList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.datasource.ClusterGraphStreamType;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;

public class ClusterGraphWriter {

	public static class Directory {
		private final File directory;
		private final String processName;

		private final Map<AutonomousSoftwareDistribution, ClusterGraphWriter> outputsByCluster = new HashMap<AutonomousSoftwareDistribution, ClusterGraphWriter>();

		public Directory(File directory, String processName) {
			this.directory = directory;
			this.processName = processName;
		}

		public void establishClusterWriters(AutonomousSoftwareDistribution cluster) throws IOException {
			ClusterGraphWriter writer = getWriter(cluster);
			if (writer == null) {
				writer = new ClusterGraphWriter(cluster, directory, processName);
				outputsByCluster.put(cluster, writer);
			}
		}

		public ClusterGraphWriter getWriter(AutonomousSoftwareDistribution cluster) {
			return outputsByCluster.get(cluster);
		}

		public void flush() throws IOException {
			for (ClusterGraphWriter output : outputsByCluster.values()) {
				output.flush();
			}
		}
	}

	final LittleEndianOutputStream nodeStream;
	final LittleEndianOutputStream edgeStream;
	final BufferedWriter moduleWriter;

	ClusterGraphWriter(AutonomousSoftwareDistribution cluster, File outputDir, String processName) throws IOException {
		String outputFilename = String.format("%s.%s.%s.%s", processName, cluster.id,
				ClusterGraphStreamType.GRAPH_NODE.id, ClusterGraphStreamType.GRAPH_NODE.extension);
		File outputFile = new File(outputDir, outputFilename);
		nodeStream = new LittleEndianOutputStream(outputFile);

		outputFilename = String.format("%s.%s.%s.%s", processName, cluster.id, ClusterGraphStreamType.GRAPH_EDGE.id,
				ClusterGraphStreamType.GRAPH_EDGE.extension);
		outputFile = new File(outputDir, outputFilename);
		edgeStream = new LittleEndianOutputStream(outputFile);

		outputFilename = String.format("%s.%s.%s.%s", processName, cluster.id, ClusterGraphStreamType.MODULE.id,
				ClusterGraphStreamType.MODULE.extension);
		outputFile = new File(outputDir, outputFilename);
		moduleWriter = new BufferedWriter(new FileWriter(outputFile));
	}

	public void writeNode(ClusterNode node) throws IOException {
		long word = node.getKey().module.id;
		word |= ((long) node.getKey().relativeTag) << 0x10;
		word |= ((long) node.getKey().instanceId) << 0x28;
		word |= ((long) node.getType().ordinal()) << 0x30;
		nodeStream.writeLong(word);
		nodeStream.writeLong(node.getHash());
	}

	public void writeEdge(Edge<ClusterNode> edge) throws IOException {
		ClusterNode fromNode = edge.getFromNode();
		long word = fromNode.getKey().module.id;
		word |= ((long) fromNode.getKey().relativeTag) << 0x10;
		word |= ((long) fromNode.getKey().instanceId) << 0x28;
		word |= ((long) edge.getEdgeType().ordinal()) << 0x30;
		word |= ((long) edge.getOrdinal()) << 0x38;
		edgeStream.writeLong(word);

		ClusterNode toNode = edge.getToNode();
		word = toNode.getKey().module.id;
		word |= ((long) toNode.getKey().relativeTag) << 0x10;
		word |= ((long) toNode.getKey().instanceId) << 0x28;
		edgeStream.writeLong(word);
	}

	public void writeModules(ClusterModuleList modules) throws IOException {
		for (ClusterModule module : modules.sortById()) {
			moduleWriter.write(String.format("%s-%s\n", module.unit.name, module.version));
		}
	}

	public void flush() throws IOException {
		nodeStream.flush();
		nodeStream.close();
		edgeStream.flush();
		edgeStream.close();
		moduleWriter.flush();
		moduleWriter.close();
	}
}
