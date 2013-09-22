package edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader;

import java.io.IOException;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;

public class ClusterGraphEdgeFactory {

	private enum LookupType {
		NORMAL,
		CLUSTER_ENTRY,
		CLUSTER_EXIT;
	}

	private static final int ENTRY_BYTE_COUNT = 0x10;

	private final List<ClusterNode<?>> nodeList;
	private final LittleEndianInputStream input;

	ClusterGraphEdgeFactory(List<ClusterNode<?>> nodeList, LittleEndianInputStream input) {
		this.nodeList = nodeList;
		this.input = input;
	}

	boolean ready() throws IOException {
		return input.ready(ENTRY_BYTE_COUNT);
	}

	Edge<ClusterNode<?>> createEdge() throws IOException {
		long first = input.readLong();
		int fromNodeIndex = (int) (first & 0xfffffffL);
		int toNodeIndex = (int) ((first >> 0x1cL) & 0xfffffffL);
		EdgeType type = EdgeType.values()[(int) ((first >> 0x38L) & 0xfL)];
		int ordinal = (int) ((first >> 0x3cL) & 0xfL);

		ClusterNode<?> fromNode = nodeList.get(fromNodeIndex);
		ClusterNode<?> toNode = nodeList.get(toNodeIndex);

		Edge<ClusterNode<?>> edge = new Edge<ClusterNode<?>>(fromNode, toNode, type, ordinal);
		fromNode.addOutgoingEdge(edge);
		toNode.addIncomingEdge(edge);
		return edge;
	}

	void close() throws IOException {
		input.close();
	}
}
