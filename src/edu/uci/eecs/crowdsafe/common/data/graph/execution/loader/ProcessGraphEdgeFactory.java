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
import edu.uci.eecs.crowdsafe.common.log.Log;

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
		return input.ready(0x10);
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

		if (edgeOrdinal == 255) {
			Log.log("Warning: skipping edge %s with ordinal 255", new Edge<ExecutionNode>(fromNode, toNode, edgeType, edgeOrdinal));
			return;
		}

		if (loader.listener != null) {
			loader.listener.nodeLoadReference(fromNode, LoadTarget.EDGE);
			loader.listener.nodeLoadReference(toNode, LoadTarget.EDGE);
		}

		// Double check if tag1 and tag2 exist in the lookup file
		if (fromNode == null) {
			boolean fixed = false;
			if (edgeType == EdgeType.CALL_CONTINUATION) {
				fromNode = loader.hashLookupTable.get(ExecutionNode.Key.create(fromTag, fromVersion + 1, fromModule));
				if (fromNode != null) {
					fixed = true;
					Log.log("\t(Call continuation/tag version bug!)");
				}
			}
			if (!fixed) {
				Log.log("Problem at edge index %d: missing 'from' node for tag 0x%x-v%d(%s) in edge to 0x%x-v%d(%s) of type %s on ordinal %d",
						edgeIndex, fromTag, fromVersion, fromModule.unit.name, toTag, toVersion, toModule.unit.name,
						edgeType, edgeOrdinal);
				return;
			}
		}
		if (toNode == null) {
			if (edgeType == EdgeType.CALL_CONTINUATION)
				return; // discard b/c we never reached the continuation point
			else {
				boolean fixed = false;
				if (edgeType == EdgeType.INDIRECT) {
					toNode = loader.hashLookupTable.get(ExecutionNode.Key.create(toTag, toVersion + 1, toModule));
					if (toNode != null) {
						fixed = true;
						Log.log("\t(Indirect branch/tag version bug!)");
					}
				}
				if (!fixed) {
					Log.log("Problem at edge index %d: missing 'to' node for tag 0x%x-v%d(%s) in edge #%d from 0x%x-v%d(%s) of type %s on ordinal %d",
							edgeIndex, toTag, toVersion, toModule.unit.name, edgeIndex, fromTag, fromVersion,
							fromModule.unit.name, edgeType, edgeOrdinal);
					return;
				}
				/**
				 * <pre>
					throw new TagNotFoundException(
							"Failed to find the 'to' node for tag 0x%x-v%d(%s) in edge #%d from 0x%x-v%d(%s) of type %s on ordinal %d",
							toTag, toVersion, toModule.unit.name, edgeIndex, fromTag, fromVersion,
							fromModule.unit.name, edgeType, edgeOrdinal);
				 */
			}
		}

		if ((fromModule.unit != toModule.unit)
				&& (loader.graph.getModuleGraphCluster(fromModule.unit) != loader.graph
						.getModuleGraphCluster(toModule.unit))) {
			throw new InvalidGraphException(String.format(
					"Error: a normal edge\n\t[%s - %s]\ncrosses between module %s and %s", fromNode, toNode,
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
				// One weird case to deal with here:
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
		if (input.ready())
			Log.log("Warning: input stream %s has %d bytes remaining.", input.description, input.available());

		input.close();
	}
}