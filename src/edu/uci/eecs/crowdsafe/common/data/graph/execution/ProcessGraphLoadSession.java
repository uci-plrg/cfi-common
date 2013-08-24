package edu.uci.eecs.crowdsafe.common.data.graph.execution;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.CrowdSafeTraceUtil;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.exception.InvalidTagException;
import edu.uci.eecs.crowdsafe.common.exception.MultipleEdgeException;
import edu.uci.eecs.crowdsafe.common.exception.TagNotFoundException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ProcessGraphLoadSession {

	private final ProcessTraceDataSource dataSource;

	private ProcessExecutionGraph graph;
	private Map<ExecutionNode.Key, ExecutionNode> hashLookupTable;

	// Count how many wrong intra-module edges there are
	private int wrongIntraModuleEdgeCnt = 0;

	public ProcessGraphLoadSession(ProcessTraceDataSource dataSource) {
		this.dataSource = dataSource;
	}

	public ProcessExecutionGraph loadGraph() throws IOException {
		Log.log("\n --- Loading graph for %s(%d) ---",
				dataSource.getProcessName(), dataSource.getProcessId());

		ProcessExecutionModuleSet modules = ProcessModuleLoader
				.loadModules(dataSource);
		graph = new ProcessExecutionGraph(dataSource, modules);

		try {
			// Construct the tag--hash lookup table
			hashLookupTable = loadGraphNodes();
			// Then read both intra- and cross- module edges from files
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
	private Map<ExecutionNode.Key, ExecutionNode> loadGraphNodes()
			throws IOException {
		Map<ExecutionNode.Key, ExecutionNode> hashLookupTable = new HashMap<ExecutionNode.Key, ExecutionNode>();

		LittleEndianInputStream input = new LittleEndianInputStream(
				dataSource
						.getDataInputStream(ProcessTraceStreamType.GRAPH_HASH));
		try {
			long tag = 0, tagOriginal = 0, hash = 0;
			long blockIndex = -1L;

			while (input.ready()) {
				tagOriginal = input.readLong();
				hash = input.readLong();
				blockIndex++;

				tag = CrowdSafeTraceUtil.getTagEffectiveValue(tagOriginal);
				int versionNumber = CrowdSafeTraceUtil
						.getNodeVersionNumber(tagOriginal);
				int metaNodeVal = CrowdSafeTraceUtil
						.getNodeMetaVal(tagOriginal);
				ModuleInstance module = graph.getModules()
						.getModuleForLoadedBlock(tag, blockIndex);

				MetaNodeType metaNodeType = MetaNodeType.values()[metaNodeVal];
				ExecutionNode node = new ExecutionNode(module, metaNodeType,
						tag, versionNumber, hash);

				// Tags don't duplicate in lookup file
				if (hashLookupTable.containsKey(node.getKey())) {
					ExecutionNode existingNode = hashLookupTable.get(node
							.getKey());
					if ((existingNode.getHash() != hash)
							&& (module.unit != SoftwareDistributionUnit.UNKNOWN)
							&& (existingNode.getModule().unit != SoftwareDistributionUnit.UNKNOWN)) {
						String msg = String.format(
								"Duplicate tags: %s -> %s in datasource %s",
								node.getKey(), existingNode,
								dataSource.toString());
						throw new InvalidTagException(msg);
					}
				}

				ModuleGraphCluster moduleCluster = graph
						.getModuleGraphCluster(module.unit);
				ModuleGraph moduleGraph = moduleCluster
						.getModuleGraph(module.unit);
				if (moduleGraph == null) {
					moduleGraph = new ModuleGraph(graph, module.unit);
					moduleCluster.addModule(moduleGraph);
				}
				moduleCluster.addNode(node);
				hashLookupTable.put(node.getKey(), node);
			}
		} finally {
			input.close();
		}

		return hashLookupTable;
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
				dataSource
						.getDataInputStream(ProcessTraceStreamType.CROSS_MODULE_GRAPH));
		try {
			long edgeIndex = -1;
			while (input.ready()) {
				long annotatedFromTag = input.readLong();
				long annotatedToTag = input.readLong();
				long signatureHash = input.readLong();
				edgeIndex++;

				long fromTag = CrowdSafeTraceUtil.getTag(annotatedFromTag);
				long toTag = CrowdSafeTraceUtil.getTag(annotatedToTag);
				int fromVersion = CrowdSafeTraceUtil
						.getTagVersion(annotatedFromTag);
				int toVersion = CrowdSafeTraceUtil
						.getTagVersion(annotatedToTag);

				ModuleInstance fromModule = graph.getModules()
						.getModuleForLoadedCrossModuleEdge(fromTag, edgeIndex);
				ModuleInstance toModule = graph.getModules()
						.getModuleForLoadedCrossModuleEdge(toTag, edgeIndex);

				EdgeType edgeType = CrowdSafeTraceUtil
						.getTagEdgeType(annotatedFromTag);
				int edgeOrdinal = CrowdSafeTraceUtil
						.getEdgeOrdinal(annotatedFromTag);

				ExecutionNode fromNode = hashLookupTable.get(ExecutionNode.Key
						.create(fromTag, fromVersion, fromModule));
				ExecutionNode toNode = hashLookupTable.get(ExecutionNode.Key
						.create(toTag, toVersion, toModule));

				// Double check if tag1 and tag2 exist in the lookup file
				if (fromNode == null) {
					throw new TagNotFoundException("0x"
							+ Long.toHexString(fromTag)
							+ " is missed in graph lookup file!");
				}
				if (toNode == null) {
					throw new TagNotFoundException("0x"
							+ Long.toHexString(toTag)
							+ " is missed in graph lookup file!");
				}

				Edge<ExecutionNode> existing = fromNode.getOutgoingEdge(toNode);
				if (existing == null) {
					// Be careful when dealing with the cross module nodes.
					// Cross-module edges are not added to any node, but the
					// edge from signature node to real entry node is preserved.
					// We only need to add the signature nodes to "nodes"
					fromNode.setMetaNodeType(MetaNodeType.MODULE_BOUNDARY);
					ModuleGraphCluster fromCluster = graph
							.getModuleGraphCluster(fromModule.unit);

					ModuleGraphCluster toCluster = graph
							.getModuleGraphCluster(toModule.unit);

					Edge<ExecutionNode> e = new Edge<ExecutionNode>(fromNode,
							toNode, edgeType, edgeOrdinal);
					if (fromCluster == toCluster) {
						fromNode.addOutgoingEdge(e);
						toNode.addIncomingEdge(e);
					} else {
						ExecutionNode exitNode = new ExecutionNode(fromModule,
								MetaNodeType.CLUSTER_EXIT, signatureHash, 0,
								signatureHash);
						fromCluster.addNode(exitNode);
						fromNode.setMetaNodeType(MetaNodeType.NORMAL);
						Edge<ExecutionNode> clusterExitEdge = new Edge<ExecutionNode>(
								fromNode, exitNode, EdgeType.CROSS_MODULE, 0);
						fromNode.addOutgoingEdge(clusterExitEdge);
						exitNode.addIncomingEdge(clusterExitEdge);

						ExecutionNode entryNode = toCluster
								.addClusterEntryNode(signatureHash, toModule);
						toNode.setMetaNodeType(MetaNodeType.NORMAL);
						Edge<ExecutionNode> clusterEntryEdge = new Edge<ExecutionNode>(
								entryNode, toNode, EdgeType.CROSS_MODULE, 0);
						entryNode.addOutgoingEdge(clusterEntryEdge);
						toNode.addIncomingEdge(clusterEntryEdge);
					}
				}
			}
		} finally {
			input.close();
		}
	}

	public void readIntraModuleEdges() throws IOException {
		LittleEndianInputStream input = new LittleEndianInputStream(
				dataSource
						.getDataInputStream(ProcessTraceStreamType.MODULE_GRAPH));

		try {
			long edgeIndex = -1;
			while (input.ready()) {
				long annotatedFromTag = input.readLong();
				long annotatedToTag = input.readLong();
				edgeIndex++;

				long fromTag = CrowdSafeTraceUtil.getTag(annotatedFromTag);
				long toTag = CrowdSafeTraceUtil.getTag(annotatedToTag);
				int fromVersion = CrowdSafeTraceUtil
						.getTagVersion(annotatedFromTag);
				int toVersion = CrowdSafeTraceUtil
						.getTagVersion(annotatedToTag);

				ModuleInstance fromModule = graph.getModules()
						.getModuleForLoadedEdge(fromTag, edgeIndex);
				ModuleInstance toModule = graph.getModules()
						.getModuleForLoadedEdge(toTag, edgeIndex);

				EdgeType edgeType = CrowdSafeTraceUtil
						.getTagEdgeType(annotatedFromTag);
				int edgeOrdinal = CrowdSafeTraceUtil
						.getEdgeOrdinal(annotatedFromTag);

				ExecutionNode node1 = hashLookupTable.get(ExecutionNode.Key
						.create(fromTag, fromVersion, fromModule));
				ExecutionNode node2 = hashLookupTable.get(ExecutionNode.Key
						.create(toTag, toVersion, toModule));

				// Double check if tag1 and tag2 exist in the lookup file
				if (node1 == null) {
					throw new TagNotFoundException("0x"
							+ Long.toHexString(fromTag)
							+ " is missed in graph lookup file!");
				}
				if (node2 == null) {
					if (edgeType == EdgeType.CALL_CONTINUATION)
						continue; // discard b/c we never reached the continuation point
					else
						throw new TagNotFoundException("0x"
								+ Long.toHexString(toTag)
								+ " is missed in graph lookup file!");
				}

				if ((fromModule.unit != toModule.unit)
						&& (fromModule.unit != SoftwareDistributionUnit.UNKNOWN)
						&& (toModule.unit != SoftwareDistributionUnit.UNKNOWN)
						&& (graph.getModuleGraphCluster(fromModule.unit) != graph
								.getModuleGraphCluster(toModule.unit))) {
					throw new InvalidGraphException(
							String.format(
									"Error: a normal edge [%s - %s] crosses between module %s and %s",
									node1,
									node2,
									graph.getModuleGraphCluster(fromModule.unit).distribution.name,
									graph.getModuleGraphCluster(toModule.unit).distribution.name));
				}

				Edge<ExecutionNode> existing = node1.getOutgoingEdge(node2);
				Edge<ExecutionNode> e = new Edge<ExecutionNode>(node1, node2,
						edgeType, edgeOrdinal);
				if (existing == null) {
					node1.addOutgoingEdge(e);
					node2.addIncomingEdge(e);
				} else {
					if (!existing.equals(e)) {
						// One wired case to deal with here:
						// A call edge (direct) and a continuation edge can
						// point to the same block
						if ((existing.getEdgeType() == EdgeType.DIRECT && e
								.getEdgeType() == EdgeType.CALL_CONTINUATION)
								|| (existing.getEdgeType() == EdgeType.CALL_CONTINUATION && e
										.getEdgeType() == EdgeType.DIRECT)) {
							existing.setEdgeType(EdgeType.DIRECT);
						} else {
							String msg = "Multiple edges:\n" + "Edge1: "
									+ existing + "\n" + "Edge2: " + e;
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
			Log.log("There are " + wrongIntraModuleEdgeCnt
					+ " cross-module edges in the intra-module edge file");
		}
	}
}