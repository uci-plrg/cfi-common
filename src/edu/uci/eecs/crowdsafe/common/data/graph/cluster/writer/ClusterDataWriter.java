package edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeIdentifier;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDataSink;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDirectory;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceStreamType;

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
		private final ClusterTraceDataSink dataSink;
		private final String filenameFormat;

		private final Map<AutonomousSoftwareDistribution, ClusterDataWriter<NodeType>> outputsByCluster = new HashMap<AutonomousSoftwareDistribution, ClusterDataWriter<NodeType>>();

		public Directory(File directory, String processName) {
			dataSink = new ClusterTraceDirectory(directory);
			filenameFormat = String.format("%s.%%s.%%s.%%s", processName);
		}

		public void establishClusterWriters(ClusterData<NodeType> data) throws IOException {
			ClusterDataWriter<NodeType> writer = getWriter(data.getCluster());
			if (writer == null) {
				dataSink.addCluster(data.getCluster(), filenameFormat);
				writer = new ClusterDataWriter<NodeType>(data, dataSink);
				outputsByCluster.put(data.getCluster(), writer);
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
	final LittleEndianOutputStream callbackStream;
	final LittleEndianOutputStream edgeStream;
	final BufferedWriter moduleWriter;

	private final ClusterData<NodeType> data;

	ClusterDataWriter(ClusterData<NodeType> data, ClusterTraceDataSink dataSink) throws IOException {
		this.data = data;

		nodeStream = dataSink.getLittleEndianOutputStream(data.getCluster(), ClusterTraceStreamType.GRAPH_NODE);
		callbackStream = dataSink.getLittleEndianOutputStream(data.getCluster(), ClusterTraceStreamType.CALLBACK_ENTRY);
		edgeStream = dataSink.getLittleEndianOutputStream(data.getCluster(), ClusterTraceStreamType.GRAPH_EDGE);
		moduleWriter = new BufferedWriter(new OutputStreamWriter(dataSink.getDataOutputStream(data.getCluster(),
				ClusterTraceStreamType.MODULE)));
	}

	public void writeNode(NodeType node) throws IOException {
		long word = data.getModuleIndex(node.getModule());
		word |= ((long) node.getRelativeTag() & 0xffffffL) << 0x10;
		word |= ((long) node.getInstanceId()) << 0x28;
		word |= ((long) node.getType().ordinal()) << 0x30;
		nodeStream.writeLong(word);
		nodeStream.writeLong(node.getHash());
	}

	public void writeCallback(NodeType node) throws IOException {
		int word = data.getModuleIndex(node.getModule());
		word |= ((int) node.getRelativeTag() & 0xffffffL) << 0x10;
		callbackStream.writeInt(word);
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

			moduleWriter.write(module.unit.name);
		}
	}

	public void flush() throws IOException {
		nodeStream.flush();
		nodeStream.close();
		callbackStream.flush();
		callbackStream.close();
		edgeStream.flush();
		edgeStream.close();
		moduleWriter.flush();
		moduleWriter.close();
	}
}
