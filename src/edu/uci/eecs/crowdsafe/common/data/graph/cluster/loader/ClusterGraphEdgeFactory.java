package edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ClusterGraphEdgeFactory {

	private enum LookupType {
		NORMAL,
		CLUSTER_ENTRY,
		CLUSTER_EXIT;
	}

	private static final int ENTRY_BYTE_COUNT = 0x8;

	private final List<ClusterNode<?>> nodeList;
	private final LittleEndianInputStream input;

	private final Map<Long, Edge<ClusterNode<?>>> existingEdges = new HashMap<Long, Edge<ClusterNode<?>>>();

	ClusterGraphEdgeFactory(List<ClusterNode<?>> nodeList, LittleEndianInputStream input) {
		this.nodeList = nodeList;
		this.input = input;
	}

	boolean ready() throws IOException {
		return input.ready(ENTRY_BYTE_COUNT);
	}

	Edge<ClusterNode<?>> createEdge() throws IOException {
		long first = input.readLong();
		Edge<ClusterNode<?>> edge = existingEdges.get(first);
		if (edge != null) {
			Log.log("Error: duplicate edge 0x%x", first);
			return edge;
		}

		int fromNodeIndex = (int) (first & 0xfffffffL);
		int toNodeIndex = (int) ((first >> 0x1cL) & 0xfffffffL);
		EdgeType type = EdgeType.values()[(int) ((first >> 0x38L) & 0xfL)];
		int ordinal = (int) ((first >> 0x3cL) & 0xfL);

		ClusterNode<?> fromNode = nodeList.get(fromNodeIndex);
		ClusterNode<?> toNode = nodeList.get(toNodeIndex);

		edge = new Edge<ClusterNode<?>>(fromNode, toNode, type, ordinal);
		existingEdges.put(first, edge);

		fromNode.addOutgoingEdge(edge);
		toNode.addIncomingEdge(edge);
		
		if (toNode.getType() == MetaNodeType.CLUSTER_EXIT)
			toString();
		
		return edge;
	}

	void close() throws IOException {
		if (input.ready())
			Log.log("Warning: input stream %s has %d bytes remaining.", input.description, input.available());

		input.close();
	}
}
