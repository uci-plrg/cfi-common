package edu.uci.eecs.crowdsafe.common.data.graph.execution.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.CrowdSafeTraceUtil;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessGraphLoadSession.LoadTarget;
import edu.uci.eecs.crowdsafe.common.exception.InvalidTagException;
import edu.uci.eecs.crowdsafe.common.exception.MultipleEdgeException;
import edu.uci.eecs.crowdsafe.common.exception.TagNotFoundException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;

/**
 * Before calling this function, you should have all the normal nodes added to the corresponding graph and their indexes
 * fixed. The only thing this function should do is to add signature nodes when necessary and build the necessary edges
 * between them and real entry nodes.
 * 
 * @param crossModuleEdgeFile
 * @param hashLookupTable
 * @throws MultipleEdgeException
 * @throws InvalidTagException
 * @throws TagNotFoundException
 */
public class ProcessGraphCrossModuleEdgeFactory {
	private final ProcessGraphLoadSession.GraphLoader loader;
	private final LittleEndianInputStream input;

	long edgeIndex = -1;

	public ProcessGraphCrossModuleEdgeFactory(ProcessGraphLoadSession.GraphLoader loader, LittleEndianInputStream input)
			throws IOException {
		this.loader = loader;
		this.input = input;
	}

	boolean ready() throws IOException {
		return input.ready(0x18);
	}

	void createEdge() throws IOException {
		long annotatedFromTag = input.readLong();
		long annotatedToTag = input.readLong();
		long signatureHash = input.readLong();
		edgeIndex++;

		long fromTag = CrowdSafeTraceUtil.getTag(annotatedFromTag);
		long toTag = CrowdSafeTraceUtil.getTag(annotatedToTag);
		int fromVersion = CrowdSafeTraceUtil.getTagVersion(annotatedFromTag);
		int toVersion = CrowdSafeTraceUtil.getTagVersion(annotatedToTag);

		ModuleInstance fromModule = loader.graph.getModules().getModuleForLoadedCrossModuleEdge(fromTag, edgeIndex);
		ModuleInstance toModule = loader.graph.getModules().getModuleForLoadedCrossModuleEdge(toTag, edgeIndex);

		EdgeType edgeType = CrowdSafeTraceUtil.getTagEdgeType(annotatedFromTag);
		int edgeOrdinal = CrowdSafeTraceUtil.getEdgeOrdinal(annotatedFromTag);

		ExecutionNode fromNode = loader.hashLookupTable.get(ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
		ExecutionNode toNode = loader.hashLookupTable.get(ExecutionNode.Key.create(toTag, toVersion, toModule));

		// Double check if tag1 and tag2 exist in the lookup file
		if (fromNode == null) {
			throw new TagNotFoundException("Failed to find cross-module edge source block %s!",
					ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
		}
		if (toNode == null) {
			throw new TagNotFoundException("Failed to find cross-module edge destination block %s!",
					ExecutionNode.Key.create(toTag, toVersion, toModule));
		}

		if (loader.listener != null) {
			loader.listener.nodeLoadReference(fromNode, LoadTarget.CROSS_MODULE_EDGE);
			loader.listener.nodeLoadReference(toNode, LoadTarget.CROSS_MODULE_EDGE);
		}

		Edge<ExecutionNode> existing = fromNode.getOutgoingEdge(toNode);
		if (existing == null) {
			// Be careful when dealing with the cross module nodes.
			// Cross-module edges are not added to any node, but the
			// edge from signature node to real entry node is preserved.
			// We only need to add the signature nodes to "nodes"
			fromNode.setMetaNodeType(MetaNodeType.MODULE_BOUNDARY);
			ModuleGraphCluster fromCluster = loader.graph.getModuleGraphCluster(fromModule.unit);

			ModuleGraphCluster toCluster = loader.graph.getModuleGraphCluster(toModule.unit);

			if (fromCluster == toCluster) {
				Edge<ExecutionNode> e = new Edge<ExecutionNode>(fromNode, toNode, edgeType, edgeOrdinal);
				fromNode.addOutgoingEdge(e);
				toNode.addIncomingEdge(e);

				if (loader.listener != null)
					loader.listener.edgeCreation(e);
			} else {
				ExecutionNode exitNode = new ExecutionNode(fromModule, MetaNodeType.CLUSTER_EXIT, signatureHash, 0,
						signatureHash, fromNode.getTimestamp());
				fromCluster.addNode(exitNode);
				fromNode.setMetaNodeType(MetaNodeType.NORMAL);
				Edge<ExecutionNode> clusterExitEdge = new Edge<ExecutionNode>(fromNode, exitNode, edgeType, 0);
				// TODO: need CROSS_MODULE for anything?
				fromNode.addOutgoingEdge(clusterExitEdge);
				exitNode.addIncomingEdge(clusterExitEdge);

				if (loader.listener != null)
					loader.listener.edgeCreation(clusterExitEdge);

				ExecutionNode entryNode = toCluster.addClusterEntryNode(signatureHash, toModule, toNode.getTimestamp());
				toNode.setMetaNodeType(MetaNodeType.NORMAL);
				Edge<ExecutionNode> clusterEntryEdge = new Edge<ExecutionNode>(entryNode, toNode,
						EdgeType.MODULE_ENTRY, 0);
				entryNode.addOutgoingEdge(clusterEntryEdge);
				toNode.addIncomingEdge(clusterEntryEdge);

				if (loader.listener != null)
					loader.listener.edgeCreation(clusterEntryEdge);
			}
		}
	}

	void close() throws IOException {
		if (input.ready())
			Log.log("Warning: input stream %s has %d bytes remaining.", input.description, input.available());

		input.close();
	}
}