package edu.uci.eecs.crowdsafe.common.data.graph;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.data.results.NodeResultsFactory;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeCollections;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeDebug;

public class ModuleGraphCluster<EdgeEndpointType extends Node<EdgeEndpointType>> {

	private static class ModuleGraphSorter implements Comparator<ModuleGraph> {
		static final ModuleGraphSorter INSTANCE = new ModuleGraphSorter();

		@Override
		public int compare(ModuleGraph first, ModuleGraph second) {
			return second.getExecutableBlockCount() - first.getExecutableBlockCount();
		}
	}

	public final AutonomousSoftwareDistribution cluster;

	// Maps from the signature hash of the cross-module edge to entry points
	private final Map<Long, EdgeEndpointType> entryNodes = new HashMap<Long, EdgeEndpointType>();

	protected final GraphData<EdgeEndpointType> graphData;

	private final Map<SoftwareDistributionUnit, ModuleGraph> graphs = new HashMap<SoftwareDistributionUnit, ModuleGraph>();
	private final Set<EdgeEndpointType> unreachableNodes = new HashSet<EdgeEndpointType>();

	private int executableNodeCount = 0;

	public ModuleGraphCluster(AutonomousSoftwareDistribution cluster) {
		this.cluster = cluster;
		this.graphData = new GraphData<EdgeEndpointType>();
	}

	public GraphData<EdgeEndpointType> getGraphData() {
		return graphData;
	}

	public ModuleGraph getModuleGraph(SoftwareDistributionUnit softwareUnit) {
		return graphs.get(softwareUnit);
	}

	public void addModule(ModuleGraph moduleGraph) {
		graphs.put(moduleGraph.softwareUnit, moduleGraph);
	}

	public Collection<ModuleGraph> getGraphs() {
		return graphs.values();
	}

	public boolean isCompatible(ModuleGraphCluster<?> other) {
		if (!cluster.equals(other.cluster))
			return false;

		for (ModuleGraph module : graphs.values()) {
			ModuleGraph otherModule = other.graphs.get(module);
			if ((otherModule != null) && !otherModule.version.equals(module.version))
				return false;
		}

		return true;
	}

	public Set<EdgeEndpointType> getUnreachableNodes() {
		return unreachableNodes;
	}

	public Collection<Long> getEntryHashes() {
		return entryNodes.keySet();
	}

	public EdgeEndpointType getEntryPoint(long hash) {
		return entryNodes.get(hash);
	}

	public void addClusterEntryNode(EdgeEndpointType entryNode) {
		if (entryNodes.containsKey(entryNode.getHash()))
			return;

		entryNodes.put(entryNode.getHash(), entryNode);
	}

	public int getExecutableNodeCount() {
		return executableNodeCount;
	}

	public boolean hasNode(Node.Key key) {
		return graphData.nodesByKey.containsKey(key);
	}

	public boolean hasNodes() {
		return !graphData.nodesByKey.isEmpty();
	}

	public Collection<Node.Key> getAllKeys() {
		return graphData.nodesByKey.keySet();
	}

	public Collection<EdgeEndpointType> getAllNodes() {
		return graphData.nodesByKey.values();
	}

	public int getNodeCount() {
		return graphData.nodesByKey.size();
	}

	public EdgeEndpointType getNode(Node.Key key) {
		return graphData.nodesByKey.get(key);
	}

	public void addNode(EdgeEndpointType node) {
		graphData.nodesByKey.put(node.getKey(), node);

		switch (node.getType()) {
			case CLUSTER_ENTRY:
				addClusterEntryNode(node);
				//$FALL-THROUGH$
			case CLUSTER_EXIT:
				return;
			default:
				graphData.nodesByHash.add(node);
				executableNodeCount++;
				graphs.get(node.getModule().unit).incrementExecutableBlockCount();

		}
	}

	public void findUnreachableNodes() {
		unreachableNodes.clear();
		unreachableNodes.addAll(graphData.nodesByKey.values());
		Set<EdgeEndpointType> visitedNodes = new HashSet<EdgeEndpointType>();
		Queue<EdgeEndpointType> bfsQueue = new LinkedList<EdgeEndpointType>();
		bfsQueue.addAll(entryNodes.values());

		while (bfsQueue.size() > 0) {
			EdgeEndpointType node = bfsQueue.remove();
			unreachableNodes.remove(node);
			visitedNodes.add(node);

			OrdinalEdgeList<EdgeEndpointType> edgeList = node.getOutgoingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					EdgeEndpointType neighbor = edge.getToNode();
					if (!visitedNodes.contains(neighbor)) {
						bfsQueue.add(neighbor);
						visitedNodes.add(neighbor);
					}
				}
			} finally {
				edgeList.release();
			}

			Edge<EdgeEndpointType> continuationEdge = node.getCallContinuation();
			if (continuationEdge != null) {
				EdgeEndpointType continuation = continuationEdge.getToNode();
				if (!visitedNodes.contains(continuation)) {
					bfsQueue.add(continuation);
					visitedNodes.add(continuation);
				}
			}
		}

		Log.log("%d unreachable nodes for cluster %s", unreachableNodes.size(), cluster.name);

		if (!CrowdSafeDebug.LOG_UNREACHABLE_ENTRY_POINTS)
			return;

		Set<EdgeEndpointType> missedEntries = new HashSet<EdgeEndpointType>();
		for (EdgeEndpointType node : unreachableNodes) {
			boolean reachableFromUnreachables = false;
			for (Edge<EdgeEndpointType> edge : node.getIncomingEdges()) {
				if (unreachableNodes.contains(edge.getFromNode())) {
					reachableFromUnreachables = true;
					break;
				}
			}
			if (!reachableFromUnreachables)
				missedEntries.add(node);
		}

		int limitCounter = 0;
		for (EdgeEndpointType node : missedEntries) {
			if (++limitCounter == CrowdSafeDebug.MAX_UNREACHABLE_NODE_REPORT) {
				Log.log("\t...");
				break;
			}

			if (node.hasIncomingEdges()) {
				for (Edge<EdgeEndpointType> edge : node.getIncomingEdges()) {
					Log.log("\tMissed incoming edge %s", edge);
				}
			} else {
				Log.log("\tNo entry points into %s", node);
			}
		}
	}

	public Graph.Cluster summarize() {
		Graph.Cluster.Builder clusterBuilder = Graph.Cluster.newBuilder();
		Graph.Module.Builder moduleBuilder = Graph.Module.newBuilder();
		Graph.ModuleInstance.Builder moduleInstanceBuilder = Graph.ModuleInstance.newBuilder();
		Graph.UnreachableNode.Builder unreachableBuilder = Graph.UnreachableNode.newBuilder();
		Graph.Node.Builder nodeBuilder = Graph.Node.newBuilder();
		Graph.Edge.Builder edgeBuilder = Graph.Edge.newBuilder();
		NodeResultsFactory nodeFactory = new NodeResultsFactory(moduleBuilder, nodeBuilder);

		int clusterNodeCount = getNodeCount();

		clusterBuilder.setDistributionName(cluster.name);
		clusterBuilder.setNodeCount(clusterNodeCount);
		clusterBuilder.setExecutableNodeCount(getExecutableNodeCount());
		clusterBuilder.setEntryPointCount(getEntryHashes().size());

		for (ModuleGraph moduleGraph : CrowdSafeCollections.createSortedCopy(getGraphs(), ModuleGraphSorter.INSTANCE)) {
			moduleBuilder.clear().setName(moduleGraph.softwareUnit.filename);
			moduleBuilder.setVersion(moduleGraph.version);
			moduleInstanceBuilder.setModule(moduleBuilder.build());
			moduleInstanceBuilder.setNodeCount(moduleGraph.getExecutableBlockCount());
			clusterBuilder.addModule(moduleInstanceBuilder.build());
		}

		Set<EdgeEndpointType> unreachableNodes = getUnreachableNodes();
		if (!unreachableNodes.isEmpty()) {
			for (Node<?> unreachableNode : unreachableNodes) {
				unreachableBuilder.clear().setNode(nodeFactory.buildNode(unreachableNode));
				unreachableBuilder.setIsEntryPoint(true);

				OrdinalEdgeList<?> edgeList = unreachableNode.getIncomingEdges();
				try {
					if (!edgeList.isEmpty()) {
						for (Edge<?> incoming : edgeList) {
							if (unreachableNodes.contains(incoming.getFromNode())) {
								unreachableBuilder.setIsEntryPoint(false);
							} else {
								moduleBuilder.setName(incoming.getFromNode().getModule().unit.filename);
								moduleBuilder.setVersion(incoming.getFromNode().getModule().version);
								nodeBuilder.setModule(moduleBuilder.build());
								// TODO: what node is it?? from the factory, earlier??
								edgeBuilder.clear().setFromNode(nodeBuilder.build());
								edgeBuilder.setToNode(unreachableBuilder.getNode());
								edgeBuilder.setType(incoming.getEdgeType().mapToResultType());
								unreachableBuilder.addMissedIncomingEdge(edgeBuilder.build());
							}
						}
					}
				} finally {
					edgeList.release();
				}
				clusterBuilder.addUnreachable(unreachableBuilder.build());
			}
		}
		return clusterBuilder.build();
	}

	@Override
	public String toString() {
		return "graph " + cluster.name;
	}
}
