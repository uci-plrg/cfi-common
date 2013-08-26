package edu.uci.eecs.crowdsafe.common.data.graph.execution;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.CrowdSafeTraceUtil;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.exception.InvalidTagException;
import edu.uci.eecs.crowdsafe.common.exception.MultipleEdgeException;
import edu.uci.eecs.crowdsafe.common.exception.TagNotFoundException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ProcessGraphLoadSession {

	public enum LoadTarget {
		NODE("node"),
		EDGE("edge"),
		CROSS_MODULE_EDGE("cross-module edge");

		public final String displayName;

		private LoadTarget(String displayName) {
			this.displayName = displayName;
		}
	}

	public interface LoadEventListener {
		void nodeLoadReference(long tag, long hash, LoadTarget target);

		void nodeLoadReference(Node node, LoadTarget target);

		void nodeCreation(Node node);

		void graphAddition(Node node, ModuleGraphCluster cluster);

		void edgeCreation(Edge edge);
	}

	private final ProcessTraceDataSource dataSource;

	private ProcessExecutionGraph graph;
	private Map<ExecutionNode.Key, ExecutionNode> hashLookupTable = new HashMap<ExecutionNode.Key, ExecutionNode>();

	// Count how many wrong intra-module edges there are
	private int wrongIntraModuleEdgeCnt = 0;

	private LoadEventListener listener = null;

	public ProcessGraphLoadSession(ProcessTraceDataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void setListener(LoadEventListener listener) {
		this.listener = listener;
	}

	public ProcessExecutionGraph loadGraph() throws IOException {
		Log.log("\n --- Loading graph for %s(%d) ---", dataSource.getProcessName(), dataSource.getProcessId());

		ProcessExecutionModuleSet modules = ProcessModuleLoader.loadModules(dataSource);
		graph = new ProcessExecutionGraph(dataSource, modules);

		try {
			loadGraphNodes();
			readIntraModuleEdges();
			readCrossModuleEdges();
		} catch (InvalidTagException e) {
			throw new InvalidGraphException(e);
		} catch (TagNotFoundException e) {
			throw new InvalidGraphException(e);
		} catch (MultipleEdgeException e) {
			throw new InvalidGraphException(e);
		}

		// Some other initialization and sanity checks
		for (ModuleGraphCluster cluster : graph.getAutonomousClusters()) {
			cluster.getGraphData().validate();
			cluster.findUnreachableNodes();
		}

		// Produce some analysis result for the graph
		ProcessExecutionGraphSummary.summarizeGraph(graph);

		return graph;
	}

	/**
	 * <p>
	 * The format of the lookup file is as the following:
	 * </p>
	 * <p>
	 * Each entry consists of 8-byte tag + 8-byte hash.
	 * </p>
	 * <p>
	 * 8-byte tag: 1-byte version number | 1-byte node type | 6-byte tag
	 * </p>
	 * 
	 * 
	 * @param lookupFiles
	 * @return
	 * @throws InvalidTagException
	 */
	private void loadGraphNodes() throws IOException {
		NodeFactory factory = new NodeFactory();
		try {
			if (factory.input.ready()) {
				factory.createNode();
				factory.createEntryPoint();
			}

			while (factory.input.ready()) {
				factory.createNode();
			}
		} finally {
			factory.input.close();
		}
	}

	public void readIntraModuleEdges() throws IOException {
		LittleEndianInputStream input = new LittleEndianInputStream(
				dataSource.getDataInputStream(ProcessTraceStreamType.MODULE_GRAPH));

		try {
			long edgeIndex = -1;
			while (input.ready()) {
				long annotatedFromTag = input.readLong();
				long annotatedToTag = input.readLong();
				edgeIndex++;

				long fromTag = CrowdSafeTraceUtil.getTag(annotatedFromTag);
				long toTag = CrowdSafeTraceUtil.getTag(annotatedToTag);
				int fromVersion = CrowdSafeTraceUtil.getTagVersion(annotatedFromTag);
				int toVersion = CrowdSafeTraceUtil.getTagVersion(annotatedToTag);

				ModuleInstance fromModule = graph.getModules().getModuleForLoadedEdge(fromTag, edgeIndex);
				ModuleInstance toModule = graph.getModules().getModuleForLoadedEdge(toTag, edgeIndex);

				EdgeType edgeType = CrowdSafeTraceUtil.getTagEdgeType(annotatedFromTag);
				int edgeOrdinal = CrowdSafeTraceUtil.getEdgeOrdinal(annotatedFromTag);

				ExecutionNode fromNode = hashLookupTable
						.get(ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
				ExecutionNode toNode = hashLookupTable.get(ExecutionNode.Key.create(toTag, toVersion, toModule));

				if (listener != null) {
					listener.nodeLoadReference(fromNode, LoadTarget.EDGE);
					listener.nodeLoadReference(toNode, LoadTarget.EDGE);
				}

				// Double check if tag1 and tag2 exist in the lookup file
				if (fromNode == null) {
					throw new TagNotFoundException("0x" + Long.toHexString(fromTag)
							+ " is missed in graph lookup file!");
				}
				if (toNode == null) {
					if (edgeType == EdgeType.CALL_CONTINUATION)
						continue; // discard b/c we never reached the continuation point
					else
						throw new TagNotFoundException("0x" + Long.toHexString(toTag)
								+ " is missed in graph lookup file!");
				}

				if ((fromModule.unit != toModule.unit) && (fromModule.unit != SoftwareDistributionUnit.UNKNOWN)
						&& (toModule.unit != SoftwareDistributionUnit.UNKNOWN)
						&& (graph.getModuleGraphCluster(fromModule.unit) != graph.getModuleGraphCluster(toModule.unit))) {
					throw new InvalidGraphException(String.format(
							"Error: a normal edge [%s - %s] crosses between module %s and %s", fromNode, toNode,
							graph.getModuleGraphCluster(fromModule.unit).distribution.name,
							graph.getModuleGraphCluster(toModule.unit).distribution.name));
				}

				Edge<ExecutionNode> existing = fromNode.getOutgoingEdge(toNode);
				Edge<ExecutionNode> e = new Edge<ExecutionNode>(fromNode, toNode, edgeType, edgeOrdinal);
				if (existing == null) {
					fromNode.addOutgoingEdge(e);
					toNode.addIncomingEdge(e);

					if (listener != null)
						listener.edgeCreation(e);
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
		} finally {
			input.close();
		}

		// Output the count for wrong edges if there is any
		if (wrongIntraModuleEdgeCnt > 0) {
			Log.log("There are " + wrongIntraModuleEdgeCnt + " cross-module edges in the intra-module edge file");
		}
	}

	/**
	 * Before calling this function, you should have all the normal nodes added to the corresponding graph and their
	 * indexes fixed. The only thing this function should do is to add signature nodes when necessary and build the
	 * necessary edges between them and real entry nodes.
	 * 
	 * @param crossModuleEdgeFile
	 * @param hashLookupTable
	 * @throws MultipleEdgeException
	 * @throws InvalidTagException
	 * @throws TagNotFoundException
	 */
	public void readCrossModuleEdges() throws IOException {
		LittleEndianInputStream input = new LittleEndianInputStream(
				dataSource.getDataInputStream(ProcessTraceStreamType.CROSS_MODULE_GRAPH));
		try {
			long edgeIndex = -1;
			while (input.ready()) {
				long annotatedFromTag = input.readLong();
				long annotatedToTag = input.readLong();
				long signatureHash = input.readLong();
				edgeIndex++;

				long fromTag = CrowdSafeTraceUtil.getTag(annotatedFromTag);
				long toTag = CrowdSafeTraceUtil.getTag(annotatedToTag);
				int fromVersion = CrowdSafeTraceUtil.getTagVersion(annotatedFromTag);
				int toVersion = CrowdSafeTraceUtil.getTagVersion(annotatedToTag);

				ModuleInstance fromModule = graph.getModules().getModuleForLoadedCrossModuleEdge(fromTag, edgeIndex);
				ModuleInstance toModule = graph.getModules().getModuleForLoadedCrossModuleEdge(toTag, edgeIndex);

				EdgeType edgeType = CrowdSafeTraceUtil.getTagEdgeType(annotatedFromTag);
				int edgeOrdinal = CrowdSafeTraceUtil.getEdgeOrdinal(annotatedFromTag);

				ExecutionNode fromNode = hashLookupTable
						.get(ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
				ExecutionNode toNode = hashLookupTable.get(ExecutionNode.Key.create(toTag, toVersion, toModule));

				// Double check if tag1 and tag2 exist in the lookup file
				if (fromNode == null) {
					throw new TagNotFoundException("0x" + Long.toHexString(fromTag)
							+ " is missed in graph lookup file!");
				}
				if (toNode == null) {
					throw new TagNotFoundException("0x" + Long.toHexString(toTag) + " is missed in graph lookup file!");
				}

				if (listener != null) {
					listener.nodeLoadReference(fromNode, LoadTarget.CROSS_MODULE_EDGE);
					listener.nodeLoadReference(toNode, LoadTarget.CROSS_MODULE_EDGE);
				}

				Edge<ExecutionNode> existing = fromNode.getOutgoingEdge(toNode);
				if (existing == null) {
					// Be careful when dealing with the cross module nodes.
					// Cross-module edges are not added to any node, but the
					// edge from signature node to real entry node is preserved.
					// We only need to add the signature nodes to "nodes"
					fromNode.setMetaNodeType(MetaNodeType.MODULE_BOUNDARY);
					ModuleGraphCluster fromCluster = graph.getModuleGraphCluster(fromModule.unit);

					ModuleGraphCluster toCluster = graph.getModuleGraphCluster(toModule.unit);

					if (fromCluster == toCluster) {
						Edge<ExecutionNode> e = new Edge<ExecutionNode>(fromNode, toNode, edgeType, edgeOrdinal);
						fromNode.addOutgoingEdge(e);
						toNode.addIncomingEdge(e);

						if (listener != null)
							listener.edgeCreation(e);
					} else {
						ExecutionNode exitNode = new ExecutionNode(fromModule, MetaNodeType.CLUSTER_EXIT,
								signatureHash, 0, signatureHash);
						fromCluster.addNode(exitNode);
						fromNode.setMetaNodeType(MetaNodeType.NORMAL);
						Edge<ExecutionNode> clusterExitEdge = new Edge<ExecutionNode>(fromNode, exitNode, edgeType, 0); // TODO:
																														// need
																														// CROSS_MODULE
																														// for
																														// anything?
						fromNode.addOutgoingEdge(clusterExitEdge);
						exitNode.addIncomingEdge(clusterExitEdge);

						if (listener != null)
							listener.edgeCreation(clusterExitEdge);

						ExecutionNode entryNode = toCluster.addClusterEntryNode(signatureHash, toModule);
						toNode.setMetaNodeType(MetaNodeType.NORMAL);
						Edge<ExecutionNode> clusterEntryEdge = new Edge<ExecutionNode>(entryNode, toNode,
								EdgeType.MODULE_ENTRY, 0);
						entryNode.addOutgoingEdge(clusterEntryEdge);
						toNode.addIncomingEdge(clusterEntryEdge);

						if (listener != null)
							listener.edgeCreation(clusterEntryEdge);
					}
				}
			}
		} finally {
			input.close();
		}
	}

	private class NodeFactory {
		final LittleEndianInputStream input;

		long tag = 0, tagOriginal = 0, hash = 0;
		long blockIndex = -1L;
		ModuleInstance module;
		ModuleGraphCluster moduleCluster;
		ExecutionNode node;

		public NodeFactory() throws IOException {
			input = new LittleEndianInputStream(dataSource.getDataInputStream(ProcessTraceStreamType.GRAPH_HASH));
		}

		void createNode() throws IOException {
			tagOriginal = input.readLong();
			hash = input.readLong();
			blockIndex++;

			tag = CrowdSafeTraceUtil.getTagEffectiveValue(tagOriginal);
			int versionNumber = CrowdSafeTraceUtil.getNodeVersionNumber(tagOriginal);
			int metaNodeVal = CrowdSafeTraceUtil.getNodeMetaVal(tagOriginal);
			module = graph.getModules().getModuleForLoadedBlock(tag, blockIndex);

			if (listener != null)
				listener.nodeLoadReference(tag, hash, LoadTarget.NODE);

			MetaNodeType metaNodeType = MetaNodeType.values()[metaNodeVal];
			node = new ExecutionNode(module, metaNodeType, tag, versionNumber, hash);

			if (listener != null)
				listener.nodeCreation(node);

			// Tags don't duplicate in lookup file
			if (hashLookupTable.containsKey(node.getKey())) {
				ExecutionNode existingNode = hashLookupTable.get(node.getKey());
				if ((existingNode.getHash() != hash) && (module.unit != SoftwareDistributionUnit.UNKNOWN)
						&& (existingNode.getModule().unit != SoftwareDistributionUnit.UNKNOWN)) {
					String msg = String.format("Duplicate tags: %s -> %s in datasource %s", node.getKey(),
							existingNode, dataSource.toString());
					throw new InvalidTagException(msg);
				}
			}

			moduleCluster = graph.getModuleGraphCluster(module.unit);
			ModuleGraph moduleGraph = moduleCluster.getModuleGraph(module.unit);
			if (moduleGraph == null) {
				moduleGraph = new ModuleGraph(graph, module.unit);
				moduleCluster.addModule(moduleGraph);
			}
			moduleCluster.addNode(node);
			hashLookupTable.put(node.getKey(), node);

			if (listener != null)
				listener.graphAddition(node, moduleCluster);
		}

		private void createEntryPoint() {
			ExecutionNode entryNode = moduleCluster.addClusterEntryNode(1L, module);
			Edge<ExecutionNode> clusterEntryEdge = new Edge<ExecutionNode>(entryNode, node, EdgeType.MODULE_ENTRY, 0);
			entryNode.addOutgoingEdge(clusterEntryEdge);
			node.addIncomingEdge(clusterEntryEdge);

			if (listener != null)
				listener.edgeCreation(clusterEntryEdge);
		}
	}
}
