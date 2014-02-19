package edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata.ClusterMetadata;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata.ClusterMetadataExecution;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata.ClusterMetadataSequence;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata.ClusterUIB;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata.ClusterUIBInterval;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ClusterGraphMetadataLoader {

	private static final int ENTRY_BYTE_COUNT = 0x8;

	private final LittleEndianInputStream input;
	private final List<Edge<ClusterNode<?>>> edgeList;

	ClusterGraphMetadataLoader(List<Edge<ClusterNode<?>>> edgeList, LittleEndianInputStream input) {
		this.edgeList = edgeList;
		this.input = input;
	}

	boolean ready() throws IOException {
		return input.ready(ENTRY_BYTE_COUNT);
	}

	boolean isMetadataMain() throws IOException {
		long header = input.readLong();
		return (header == 1L); // MAIN
	}

	ClusterMetadataSequence loadSequence() throws IOException {
		long header = input.readLong();
		boolean isRoot = (header & 1L) == 1L;
		int executionCount = ((int) (header >> 0x20));
		if (executionCount == 0)
			return null;

		ClusterMetadataExecution rootExecution = loadExecution();
		ClusterMetadataSequence sequence = new ClusterMetadataSequence(rootExecution.id, isRoot);
		sequence.executions.add(rootExecution);

		for (int i = 1; i < executionCount; i++) {
			sequence.executions.add(loadExecution());
		}

		return sequence;
	}

	private ClusterMetadataExecution loadExecution() throws IOException {
		long entryCounts = input.readLong();
		int uibCount = (int) (entryCounts & 0x7fffffffL);
		int intervalCount = (int) ((entryCounts >> 0x20) & 0x7fffffffL);

		long executionIdHigh = input.readLong();
		long executionIdLow = input.readLong();
		UUID executionId = new UUID(executionIdHigh, executionIdLow);
		ClusterMetadataExecution execution = new ClusterMetadataExecution(executionId);

		for (int i = 0; i < uibCount; i++) {
			long uibData = input.readLong();
			int edgeIndex = ((int) (uibData & 0xfffffL));
			int instanceCount = (int) ((uibData >> 0x14) & 0xfffL);
			int traversalCount = ((int) ((uibData >> 0x20) & 0x7fffffff));
			boolean isAdmitted = ((uibData & 0x8000000000000000L) == 0x8000000000000000L);

			// hack--correcting the cluster entry edge type modeling error
			Edge<ClusterNode<?>> uibEdge = edgeList.get(edgeIndex);
			if (!isAdmitted) { // UIB-FIX: if this is the left graph, need to check edges on the right
				OrdinalEdgeList<ClusterNode<?>> edges = uibEdge.getToNode().getIncomingEdges();
				try {
					for (Edge<ClusterNode<?>> edge : edges) {
						if ((edge != uibEdge) && (edge.getEdgeType() == EdgeType.INDIRECT)) {
							isAdmitted = true;
							break;
						}
					}
				} finally {
					edges.release();
				}
			}

			ClusterUIB uib = new ClusterUIB(uibEdge, isAdmitted, traversalCount, instanceCount);
			execution.uibs.add(uib);
		}

		// execution.initializeIntervals(); // marks the execution as main
		for (int i = 0; i < intervalCount; i++) {
			long intervalData = input.readLong();
			int typeId = ((int) (intervalData & 0xffL));
			int span = ((int) ((intervalData >> 8) & 0xffL));
			int maxConsecutive = ((int) ((intervalData >> 0x10) & 0xffffL));
			int count = ((int) ((intervalData >> 0x20) & 0x7fffffffL));
			ClusterUIBInterval interval = new ClusterUIBInterval(typeId, span, count, maxConsecutive);
			execution.addInterval(interval);
		}

		return execution;
	}

	void close() throws IOException {
		if (input.ready())
			Log.log("Warning: input stream %s has %d bytes remaining.", input.description, input.available());

		input.close();
	}
}
