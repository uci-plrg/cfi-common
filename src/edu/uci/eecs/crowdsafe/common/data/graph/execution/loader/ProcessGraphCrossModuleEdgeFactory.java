package edu.uci.eecs.crowdsafe.common.data.graph.execution.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.GraphLoadEventListener.LoadTarget;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.execution.ExecutionTraceStreamType;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeTraceUtil;

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

	private static final int ENTRY_BYTE_COUNT = 0x18;

	private final ProcessGraphLoadSession.GraphLoader loader;
	private final LittleEndianInputStream input;

	long edgeIndex = -1;

	public ProcessGraphCrossModuleEdgeFactory(ProcessGraphLoadSession.GraphLoader loader, LittleEndianInputStream input)
			throws IOException {
		this.loader = loader;
		this.input = input;
	}

	boolean ready() throws IOException {
		return input.ready(ENTRY_BYTE_COUNT);
	}

	void createEdge() throws IOException {
		long annotatedFromTag = input.readLong();
		long annotatedToTag = input.readLong();
		long signatureHash = input.readLong();
		boolean isCallback = (signatureHash < 0);
		signatureHash = Math.abs(signatureHash);
		edgeIndex++;

		long fromTag = CrowdSafeTraceUtil.getTag(annotatedFromTag);
		long toTag = CrowdSafeTraceUtil.getTag(annotatedToTag);
		int fromVersion = CrowdSafeTraceUtil.getTagVersion(annotatedFromTag);
		int toVersion = CrowdSafeTraceUtil.getTagVersion(annotatedToTag);

		ModuleInstance fromModule = loader.graph.getModules().getModule(fromTag, edgeIndex,
				ExecutionTraceStreamType.CROSS_MODULE_EDGE);
		ModuleInstance toModule = loader.graph.getModules().getModule(toTag, edgeIndex,
				ExecutionTraceStreamType.CROSS_MODULE_EDGE);

		EdgeType edgeType = CrowdSafeTraceUtil.getTagEdgeType(annotatedFromTag);
		int edgeOrdinal = CrowdSafeTraceUtil.getEdgeOrdinal(annotatedFromTag);

		ExecutionNode fromNode = loader.hashLookupTable.get(ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
		ExecutionNode toNode = loader.hashLookupTable.get(ExecutionNode.Key.create(toTag, toVersion, toModule));

		// Double check if tag1 and tag2 exist in the lookup file
		if (fromNode == null) {
			Log.log("Problem at cross-module edge index %d: missing cross-module edge source block %s!", edgeIndex,
					ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
			return;
			/**
			 * <pre>
			throw new TagNotFoundException("Failed to find cross-module edge source block %s!",
					ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
			 */
		}
		if (toNode == null) {
			Log.log("Problem at cross-module edge index %d: missing cross-module edge destination block %s!",
					edgeIndex, ExecutionNode.Key.create(toTag, toVersion, toModule));
			return;
			/**
			 * <pre>
			throw new TagNotFoundException("Failed to find cross-module edge destination block %s!",
					ExecutionNode.Key.create(toTag, toVersion, toModule));
			 */
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
			ModuleGraphCluster<ExecutionNode> fromCluster = loader.graph.getModuleGraphCluster(fromModule.unit);

			ModuleGraphCluster<ExecutionNode> toCluster = loader.graph.getModuleGraphCluster(toModule.unit);

			if (fromCluster == toCluster) {
				Edge<ExecutionNode> e = new Edge<ExecutionNode>(fromNode, toNode, edgeType, edgeOrdinal);
				fromNode.addOutgoingEdge(e);
				toNode.addIncomingEdge(e);

				if (loader.listener != null)
					loader.listener.edgeCreation(e);
			} else {
				ExecutionNode exitNode = new ExecutionNode(fromModule, MetaNodeType.CLUSTER_EXIT, signatureHash, 0,
						signatureHash, fromNode.getTimestamp(), isCallback);
				fromCluster.addNode(exitNode);
				Edge<ExecutionNode> clusterExitEdge = new Edge<ExecutionNode>(fromNode, exitNode, edgeType, 0);
				fromNode.addOutgoingEdge(clusterExitEdge);
				exitNode.addIncomingEdge(clusterExitEdge);

				if (loader.listener != null)
					loader.listener.edgeCreation(clusterExitEdge);

				ExecutionNode entryNode = toCluster.getEntryPoint(signatureHash);
				if (entryNode == null) {
					entryNode = new ExecutionNode(toModule, MetaNodeType.CLUSTER_ENTRY, 0L, 0, signatureHash,
							toNode.getTimestamp(), isCallback);
					toCluster.addClusterEntryNode(entryNode);
				}
				Edge<ExecutionNode> clusterEntryEdge = new Edge<ExecutionNode>(entryNode, toNode,
						EdgeType.CLUSTER_ENTRY, 0);
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