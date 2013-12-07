package edu.uci.eecs.crowdsafe.common.data.graph;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.data.results.NodeResultsFactory;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeCollections;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeDebug;
import edu.uci.eecs.crowdsafe.common.util.MutableInteger;

public class ModuleGraphCluster<EdgeEndpointType extends Node<EdgeEndpointType>> {

	private static class ModuleGraphSorter implements Comparator<ModuleGraph> {
		static final ModuleGraphSorter INSTANCE = new ModuleGraphSorter();

		@Override
		public int compare(ModuleGraph first, ModuleGraph second) {
			return second.getExecutableBlockCount() - first.getExecutableBlockCount();
		}
	}

	public final AutonomousSoftwareDistribution cluster;

	// Maps from the signature hash of the cross-module edge to entry/exit points
	private final Map<Long, EdgeEndpointType> entryNodes = new HashMap<Long, EdgeEndpointType>();
	private final Map<Long, EdgeEndpointType> exitNodes = new HashMap<Long, EdgeEndpointType>();

	protected final GraphData<EdgeEndpointType> graphData;

	private final Map<SoftwareUnit, ModuleGraph> graphs = new HashMap<SoftwareUnit, ModuleGraph>();

	private final Set<EdgeEndpointType> unreachableNodes = new HashSet<EdgeEndpointType>();
	private final Map<EdgeType, MutableInteger> intraModuleEdgeTypeCounts = new EnumMap<EdgeType, MutableInteger>(
			EdgeType.class);
	private final Map<EdgeType, MutableInteger> interModuleEdgeTypeCounts = new EnumMap<EdgeType, MutableInteger>(
			EdgeType.class);

	private int maxIntraModuleEdges = 0;
	private int maxCalloutEdges = 0;
	private int maxExportEdges = 0;

	private int executableNodeCount = 0;

	private boolean analyzed = false;

	public ModuleGraphCluster(AutonomousSoftwareDistribution cluster) {
		this.cluster = cluster;
		this.graphData = new GraphData<EdgeEndpointType>();

		for (EdgeType type : EdgeType.values()) {
			intraModuleEdgeTypeCounts.put(type, new MutableInteger(0));
			interModuleEdgeTypeCounts.put(type, new MutableInteger(0));
		}
	}

	public GraphData<EdgeEndpointType> getGraphData() {
		return graphData;
	}

	public ModuleGraph getModuleGraph(SoftwareUnit softwareUnit) {
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
			ModuleGraph otherModule = other.graphs.get(module.softwareUnit);
			if (otherModule == null)
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

	public Collection<EdgeEndpointType> getEntryPoints() {
		return entryNodes.values();
	}

	public EdgeEndpointType getEntryPoint(long hash) {
		return entryNodes.get(hash);
	}

	public void addClusterEntryNode(EdgeEndpointType entryNode) {
		if (entryNodes.containsKey(entryNode.getHash()))
			return;

		entryNodes.put(entryNode.getHash(), entryNode);
	}

	public Collection<Long> getExitHashes() {
		return exitNodes.keySet();
	}

	public Collection<EdgeEndpointType> getExitPoints() {
		return exitNodes.values();
	}

	public EdgeEndpointType getExitPoint(long hash) {
		return exitNodes.get(hash);
	}

	public void addClusterExitNode(EdgeEndpointType exitNode) {
		if (exitNodes.containsKey(exitNode.getHash()))
			return;

		exitNodes.put(exitNode.getHash(), exitNode);
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
		switch (node.getType()) {
			case CLUSTER_ENTRY:
				addClusterEntryNode(node);
				break;
			case CLUSTER_EXIT:
				addClusterExitNode(node);
				break;
			case SINGLETON:
				if ((node.getRelativeTag() == ClusterNode.PROCESS_ENTRY_SINGLETON)
						|| (node.getRelativeTag() == ClusterNode.SYSTEM_SINGLETON))
					return;
				break;
			default:
				graphData.nodesByHash.add(node);
				executableNodeCount++;
				graphs.get(node.getModule().unit).incrementExecutableBlockCount();

		}
		graphData.nodesByKey.put(node.getKey(), node);
	}

	public void resetAnalysis() {
		analyzed = false;
	}

	public void analyzeGraph() {
		if (analyzed)
			return;

		analyzed = true;

		unreachableNodes.clear();
		unreachableNodes.addAll(graphData.nodesByKey.values());

		intraModuleEdgeTypeCounts.clear();
		interModuleEdgeTypeCounts.clear();
		for (EdgeType type : EdgeType.values()) {
			intraModuleEdgeTypeCounts.put(type, new MutableInteger(0));
			interModuleEdgeTypeCounts.put(type, new MutableInteger(0));
		}

		int intraModuleEdges;
		int calloutEdges;
		int exportEdges;
		maxIntraModuleEdges = 0;
		maxCalloutEdges = 0;
		maxExportEdges = 0;

		Set<EdgeEndpointType> visitedNodes = new HashSet<EdgeEndpointType>();
		Queue<EdgeEndpointType> bfsQueue = new LinkedList<EdgeEndpointType>();
		bfsQueue.addAll(entryNodes.values());

		while (bfsQueue.size() > 0) {
			EdgeEndpointType node = bfsQueue.remove();
			unreachableNodes.remove(node);
			visitedNodes.add(node);

			intraModuleEdges = 0;
			calloutEdges = 0;
			exportEdges = 0;
			OrdinalEdgeList<EdgeEndpointType> edgeList = node.getOutgoingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					EdgeEndpointType neighbor = edge.getToNode();
					if (!visitedNodes.contains(neighbor)) {
						bfsQueue.add(neighbor);
						visitedNodes.add(neighbor);
					}
					switch (neighbor.getType()) {
						case CLUSTER_EXIT:
							calloutEdges++;
							interModuleEdgeTypeCounts.get(edge.getEdgeType()).increment();
							break;
						default:
							intraModuleEdges++;
							intraModuleEdgeTypeCounts.get(edge.getEdgeType()).increment();
					}
				}
			} finally {
				edgeList.release();
			}

			edgeList = node.getIncomingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					if (edge.getEdgeType() == EdgeType.CLUSTER_ENTRY)
						exportEdges++;
				}
			} finally {
				edgeList.release();
			}

			if (intraModuleEdges > maxIntraModuleEdges)
				maxIntraModuleEdges = intraModuleEdges;
			if (calloutEdges > maxCalloutEdges)
				maxCalloutEdges = calloutEdges;
			if (exportEdges > maxExportEdges)
				maxExportEdges = exportEdges;
		}

		Log.log("Max intra-module edges for a single node: %d", maxIntraModuleEdges);
		Log.log("Max callout edges for a single node: %d", maxCalloutEdges);
		Log.log("Max export edges for a single node: %d", maxExportEdges);

		/**
		 * <pre>
		for (EdgeEndpointType unreachable : new ArrayList<EdgeEndpointType>(unreachableNodes)) {
			if (unreachable.getType() == MetaNodeType.CLUSTER_EXIT) {
				OrdinalEdgeList<EdgeEndpointType> edgeList = unreachable.getIncomingEdges();
				try {
					for (Edge<EdgeEndpointType> edge : edgeList) {
						if (edge.getFromNode().getRelativeTag() == ClusterNode.PROCESS_ENTRY_SINGLETON)
							unreachableNodes.remove(unreachable);
					}
				} finally {
					edgeList.release();
				}
			}
		}
		 */
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

	public void logGraph() {
		logGraph(Integer.MAX_VALUE);
	}

	public void logGraph(int limit) {
		if (entryNodes.isEmpty()) {
			logUnreachableGraph();
			return;
		}

		Log.log("\nGraph traversal for cluster %s", cluster);

		Set<EdgeEndpointType> visitedNodes = new HashSet<EdgeEndpointType>();
		Queue<EdgeEndpointType> bfsQueue = new LinkedList<EdgeEndpointType>();
		bfsQueue.addAll(entryNodes.values());

		int count = 0;
		queue: while (bfsQueue.size() > 0) {
			EdgeEndpointType node = bfsQueue.remove();
			visitedNodes.add(node);

			OrdinalEdgeList<EdgeEndpointType> edgeList = node.getOutgoingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					EdgeEndpointType neighbor = edge.getToNode();
					if (!visitedNodes.contains(neighbor)) {
						bfsQueue.add(neighbor);
						visitedNodes.add(neighbor);
					}
					Log.log(edge);

					count++;
					if (count > limit)
						break queue;
				}
			} finally {
				edgeList.release();
			}
		}

		Log.log();
	}

	public void logUnreachableGraph() {
		Log.log("\nGraph traversal for unreachable graph %s", cluster);

		for (EdgeEndpointType node : getAllNodes()) {
			Log.log("%s", node);

			OrdinalEdgeList<EdgeEndpointType> edgeList = node.getOutgoingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					Log.log("Outgoing: %s", edge);
				}
			} finally {
				edgeList.release();
			}
			edgeList = node.getIncomingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					Log.log("Incoming: %s", edge);
				}
			} finally {
				edgeList.release();
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
		Graph.EdgeTypeCount.Builder edgeTypeCountBuilder = Graph.EdgeTypeCount.newBuilder();
		NodeResultsFactory nodeFactory = new NodeResultsFactory(moduleBuilder, nodeBuilder);

		int clusterNodeCount = getNodeCount();

		clusterBuilder.setDistributionName(cluster.name);
		clusterBuilder.setNodeCount(clusterNodeCount);
		clusterBuilder.setExecutableNodeCount(getExecutableNodeCount());
		clusterBuilder.setEntryPointCount(getEntryHashes().size());

		for (ModuleGraph moduleGraph : CrowdSafeCollections.createSortedCopy(getGraphs(), ModuleGraphSorter.INSTANCE)) {
			moduleBuilder.clear().setName(moduleGraph.softwareUnit.filename);
			moduleBuilder.setVersion(moduleGraph.softwareUnit.version);
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
								moduleBuilder.setVersion(incoming.getFromNode().getModule().unit.version);
								nodeBuilder.setModule(moduleBuilder.build());
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

		for (EdgeType type : EdgeType.values()) {
			edgeTypeCountBuilder.clear().setType(type.mapToResultType());
			edgeTypeCountBuilder.setCount(interModuleEdgeTypeCounts.get(type).getVal());
			clusterBuilder.addInterModuleEdgeCount(edgeTypeCountBuilder.build());
		}
		for (EdgeType type : EdgeType.values()) {
			edgeTypeCountBuilder.clear().setType(type.mapToResultType());
			edgeTypeCountBuilder.setCount(intraModuleEdgeTypeCounts.get(type).getVal());
			clusterBuilder.addIntraModuleEdgeCount(edgeTypeCountBuilder.build());
		}

		return clusterBuilder.build();
	}

	@Override
	public String toString() {
		return "graph " + cluster.name;
	}
}
