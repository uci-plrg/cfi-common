package edu.uci.eecs.crowdsafe.common.data.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ModuleGraphCluster<EdgeEndpointType extends Node<EdgeEndpointType>> {
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
		switch (node.getType()) {
			case CLUSTER_ENTRY:
				addClusterEntryNode(node);
				return;
			case CLUSTER_EXIT:
				graphData.nodesByKey.put(node.getKey(), node);
				return;
		}

		graphData.nodesByHash.add(node);

		if (graphData.nodesByKey.put(node.getKey(), node) == null) {
			switch (node.getType()) {
				case NORMAL:
				case RETURN:
				case TRAMPOLINE:
				case PROCESS_ENTRY:
				case PROCESS_EXIT:
				case SIGNAL_HANDLER:
				case SIGRETURN:
					executableNodeCount++;
					graphs.get(node.getModule().unit).incrementExecutableBlockCount();
			}
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

			for (Edge<EdgeEndpointType> edge : node.getOutgoingEdges()) {
				EdgeEndpointType neighbor = edge.getToNode();
				if (!visitedNodes.contains(neighbor)) {
					bfsQueue.add(neighbor);
					visitedNodes.add(neighbor);
				}
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

		for (EdgeEndpointType node : missedEntries) {
			if (node.hasIncomingEdges()) {
				for (Edge<EdgeEndpointType> edge : node.getIncomingEdges()) {
					Log.log("Missed incoming edge %s", edge);
				}
			} else {
				Log.log("No entry points into %s", node);
			}
		}
	}
}
