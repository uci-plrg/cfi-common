package edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeIdentifier;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.common.datasource.cluster.ClusterTraceStreamType;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;

public class ClusterDataWriter<NodeType extends NodeIdentifier> {

	public interface ClusterData<NodeType extends NodeIdentifier> {
		AutonomousSoftwareDistribution getCluster();

		int getModuleIndex(SoftwareModule module);

		Iterable<? extends SoftwareModule> getSortedModuleList();

		int getNodeIndex(NodeType node);
	}

	public interface Edge<NodeType extends NodeIdentifier> {
		NodeType getFromNode();

		NodeType getToNode();

		EdgeType getEdgeType();

		int getOrdinal();
	}

	public static class Directory<NodeType extends NodeIdentifier> {
		private final File directory;
		private final String processName;

		private final Map<AutonomousSoftwareDistribution, ClusterDataWriter<NodeType>> outputsByCluster = new HashMap<AutonomousSoftwareDistribution, ClusterDataWriter<NodeType>>();

		public Directory(File directory, String processName) {
			this.directory = directory;
			this.processName = processName;
		}

		public void establishClusterWriters(AutonomousSoftwareDistribution cluster, ClusterData<NodeType> data)
				throws IOException {
			ClusterDataWriter<NodeType> writer = getWriter(cluster);
			if (writer == null) {
				writer = new ClusterDataWriter<NodeType>(data, directory, processName);
				outputsByCluster.put(cluster, writer);
			}
		}

		public ClusterDataWriter<NodeType> getWriter(AutonomousSoftwareDistribution cluster) {
			return outputsByCluster.get(cluster);
		}

		public void flush() throws IOException {
			for (ClusterDataWriter<NodeType> output : outputsByCluster.values()) {
				output.flush();
			}
		}
	}

	final LittleEndianOutputStream nodeStream;
	final LittleEndianOutputStream edgeStream;
	final BufferedWriter moduleWriter;

	private final ClusterData<NodeType> data;

	ClusterDataWriter(ClusterData<NodeType> data, File outputDir, String processName) throws IOException {
		this.data = data;

		String outputFilename = String.format("%s.%s.%s.%s", processName, data.getCluster().id,
				ClusterTraceStreamType.GRAPH_NODE.id, ClusterTraceStreamType.GRAPH_NODE.extension);
		File outputFile = new File(outputDir, outputFilename);
		nodeStream = new LittleEndianOutputStream(outputFile);

		outputFilename = String.format("%s.%s.%s.%s", processName, data.getCluster().id,
				ClusterTraceStreamType.GRAPH_EDGE.id, ClusterTraceStreamType.GRAPH_EDGE.extension);
		outputFile = new File(outputDir, outputFilename);
		edgeStream = new LittleEndianOutputStream(outputFile);

		outputFilename = String.format("%s.%s.%s.%s", processName, data.getCluster().id,
				ClusterTraceStreamType.MODULE.id, ClusterTraceStreamType.MODULE.extension);
		outputFile = new File(outputDir, outputFilename);
		moduleWriter = new BufferedWriter(new FileWriter(outputFile));
	}

	public void writeNode(NodeType node) throws IOException {
		long word = data.getModuleIndex(node.getModule());
		word |= ((long) node.getRelativeTag() & 0xffffffL) << 0x10;
		word |= ((long) node.getInstanceId()) << 0x28;
		word |= ((long) node.getType().ordinal()) << 0x30;
		nodeStream.writeLong(word);
		nodeStream.writeLong(node.getHash());
	}

	public void writeEdge(Edge<NodeType> edge) throws IOException {
		long word = (long) data.getNodeIndex(edge.getFromNode());
		word |= ((long) data.getNodeIndex(edge.getToNode())) << 0x1c;
		word |= ((long) edge.getEdgeType().ordinal()) << 0x38;
		word |= ((long) edge.getOrdinal()) << 0x3c;
		edgeStream.writeLong(word);
	}

	public void writeModules() throws IOException {
		for (SoftwareModule module : data.getSortedModuleList()) {
			if (module.equals(ClusterBoundaryNode.BOUNDARY_MODULE))
				continue;

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
