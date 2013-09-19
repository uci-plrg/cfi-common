package edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModule;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;

public class ClusterGraphEdgeFactory {

	private enum LookupType {
		NORMAL,
		CLUSTER_ENTRY,
		CLUSTER_EXIT;
	}

	private static final int ENTRY_BYTE_COUNT = 0x10;

	private final ClusterGraph graph;
	private final LittleEndianInputStream input;

	private final ClusterNode.LookupKey lookupKey = new ClusterNode.LookupKey();

	ClusterGraphEdgeFactory(ClusterGraph graph, LittleEndianInputStream input) {
		this.graph = graph;
		this.input = input;
	}

	boolean ready() throws IOException {
		return input.ready(ENTRY_BYTE_COUNT);
	}

	Edge<ClusterNode> createEdge() throws IOException {
		long first = input.readLong();
		int typeByte = (int) ((first >> 0x30) & 0xffL);
		EdgeType type = EdgeType.values()[typeByte & 0xf];
		int ordinal = (int) ((first >> 0x38) & 0xffL);

		LookupType fromLookupType = LookupType.NORMAL;
		if (typeByte >= 0x80) {
			fromLookupType = LookupType.CLUSTER_ENTRY;
		}
		ClusterNode fromNode = lookupNode(first, fromLookupType);

		LookupType toLookupType = LookupType.NORMAL;
		if ((fromLookupType == LookupType.NORMAL) && (typeByte >= 0x40)) {
			toLookupType = LookupType.CLUSTER_EXIT;
		}

		long second = input.readLong();
		ClusterNode toNode = lookupNode(second, toLookupType);

		if ((fromNode == null) || (toNode == null))
			toString();
		if (toNode.getType() == MetaNodeType.CLUSTER_ENTRY)
			toNode.toString();

		Edge<ClusterNode> edge = new Edge<ClusterNode>(fromNode, toNode, type, ordinal);
		fromNode.addOutgoingEdge(edge);
		toNode.addIncomingEdge(edge);
		return edge;
	}

	private ClusterNode lookupNode(long inputField, LookupType lookupType) {
		int moduleIndex = (int) (inputField & 0xffffL);
		int relativeTag = (int) ((inputField >> 0x10) & 0xffffffL);
		int instanceId = (int) ((inputField >> 0x28) & 0xffL);
		ClusterModule module = graph.moduleList.getModule(moduleIndex);

		// TODO: change entry/exit lookup to load-local
		switch (lookupType) {
			case NORMAL:
				return graph.getGraphData().nodesByKey.get(lookupKey.setIdentity(module, relativeTag, instanceId));
			case CLUSTER_ENTRY:
				return graph.getEntryPoint(lookupKey.setIdentity(module, relativeTag, instanceId));
			case CLUSTER_EXIT:
				return graph.getExitPoint(lookupKey.setIdentity(module, relativeTag, instanceId));
			default:
				throw new IllegalStateException("Unknown lookup type " + lookupType);
		}
	}

	void close() throws IOException {
		input.close();
	}
}
