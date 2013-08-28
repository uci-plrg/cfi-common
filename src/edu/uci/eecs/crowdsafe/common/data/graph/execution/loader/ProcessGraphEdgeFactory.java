package edu.uci.eecs.crowdsafe.common.data.graph.execution.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.CrowdSafeTraceUtil;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessGraphLoadSession.LoadTarget;
import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.exception.MultipleEdgeException;
import edu.uci.eecs.crowdsafe.common.exception.TagNotFoundException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;

public class ProcessGraphEdgeFactory {
	private final ProcessGraphLoadSession.GraphLoader loader;
	private final LittleEndianInputStream input;

	long edgeIndex = -1;

	public ProcessGraphEdgeFactory(ProcessGraphLoadSession.GraphLoader loader, LittleEndianInputStream input)
			throws IOException {
		this.loader = loader;
		this.input = input;
	}

	boolean ready() throws IOException {
		return input.ready();
	}

	void createEdge() throws IOException {
		long annotatedFromTag = input.readLong();
		long annotatedToTag = input.readLong();
		edgeIndex++;

		long fromTag = CrowdSafeTraceUtil.getTag(annotatedFromTag);
		long toTag = CrowdSafeTraceUtil.getTag(annotatedToTag);
		int fromVersion = CrowdSafeTraceUtil.getTagVersion(annotatedFromTag);
		int toVersion = CrowdSafeTraceUtil.getTagVersion(annotatedToTag);

		ModuleInstance fromModule = loader.graph.getModules().getModuleForLoadedEdge(fromTag, edgeIndex);
		ModuleInstance toModule = loader.graph.getModules().getModuleForLoadedEdge(toTag, edgeIndex);

		EdgeType edgeType = CrowdSafeTraceUtil.getTagEdgeType(annotatedFromTag);
		int edgeOrdinal = CrowdSafeTraceUtil.getEdgeOrdinal(annotatedFromTag);

		ExecutionNode fromNode = loader.hashLookupTable.get(ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
		ExecutionNode toNode = loader.hashLookupTable.get(ExecutionNode.Key.create(toTag, toVersion, toModule));

		if (loader.listener != null) {
			loader.listener.nodeLoadReference(fromNode, LoadTarget.EDGE);
			loader.listener.nodeLoadReference(toNode, LoadTarget.EDGE);
		}

		// Double check if tag1 and tag2 exist in the lookup file
		if (fromNode == null) {
			throw new TagNotFoundException(
					"Failed to find the 'from' node for tag 0x%x(%s) in edge to 0x%x(%s) of type %s on ordinal %d",
					fromTag, fromModule.unit.name, toTag, toModule.unit.name, edgeType, edgeOrdinal);
		}
		if (toNode == null) {
			if (edgeType == EdgeType.CALL_CONTINUATION)
				return; // discard b/c we never reached the continuation point
			else
				throw new TagNotFoundException("0x" + Long.toHexString(toTag) + " is missed in graph lookup file!");
		}

		if ((fromModule.unit != toModule.unit)
				&& (loader.graph.getModuleGraphCluster(fromModule.unit) != loader.graph
						.getModuleGraphCluster(toModule.unit))) {
			throw new InvalidGraphException(String.format(
					"Error: a normal edge [%s - %s] crosses between module %s and %s", fromNode, toNode,
					loader.graph.getModuleGraphCluster(fromModule.unit).distribution.name,
					loader.graph.getModuleGraphCluster(toModule.unit).distribution.name));
		}

		Edge<ExecutionNode> existing = fromNode.getOutgoingEdge(toNode);
		Edge<ExecutionNode> e = new Edge<ExecutionNode>(fromNode, toNode, edgeType, edgeOrdinal);
		if (existing == null) {
			fromNode.addOutgoingEdge(e);
			toNode.addIncomingEdge(e);

			if (loader.listener != null)
				loader.listener.edgeCreation(e);
		} else {
			if (!existing.equals(e)) {
				// One wired case to deal with here:
				// A call edge (direct) and a continuation edge can
				// point to the same block
				if ((existing.getEdgeType() == EdgeType.DIRECT && e.getEdgeType() == EdgeType.CALL_CONTINUATION)
						|| (existing.getEdgeType() == EdgeType.CALL_CONTINUATION && e.getEdgeType() == EdgeType.DIRECT)) {
					existing.setEdgeType(EdgeType.DIRECT);
				} else {
					String msg = "Multiple edges:\n" + "Edge1: " + existing + "\n" + "Edge2: " + e;
					throw new MultipleEdgeException(msg);
				}
			}
		}
	}

	void close() throws IOException {
		input.close();
	}
}