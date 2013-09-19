package edu.uci.eecs.crowdsafe.common.data.graph.execution;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.GraphData;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;

public class ModuleGraphCluster<NodeType extends Node> {
	public final AutonomousSoftwareDistribution cluster;

	// Maps from signature hash to entry and exit points
	private final Map<Long, NodeType> entryNodes = new HashMap<Long, NodeType>();
	private final Map<Node.Key, NodeType> entryNodesByKey = new HashMap<Node.Key, NodeType>();
	private final Map<Long, NodeType> exitNodes = new HashMap<Long, NodeType>();
	private final Map<Node.Key, NodeType> exitNodesByKey = new HashMap<Node.Key, NodeType>();

	protected final GraphData<NodeType> graphData;

	private final Map<SoftwareDistributionUnit, ModuleGraph> graphs = new HashMap<SoftwareDistributionUnit, ModuleGraph>();
	private final Set<NodeType> unreachableNodes = new HashSet<NodeType>();

	private int executableNodeCount = 0;

	public ModuleGraphCluster(AutonomousSoftwareDistribution cluster) {
		this.cluster = cluster;
		this.graphData = new GraphData();
	}

	public GraphData<NodeType> getGraphData() {
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

	public Set<NodeType> getUnreachableNodes() {
		return unreachableNodes;
	}

	public Collection<Long> getEntryHashes() {
		return entryNodes.keySet();
	}

	public NodeType getEntryPoint(long hash) {
		return entryNodes.get(hash);
	}

	public NodeType getEntryPoint(ClusterNode.LookupKey key) {
		return entryNodesByKey.get(key);
	}

	public void addClusterEntryNode(NodeType entryNode) {
		entryNodes.put(entryNode.getHash(), entryNode);
		entryNodesByKey.put(entryNode.getKey(), entryNode);
		// if (entryNodes.put(entryNode.getHash(), entryNode) == null)
		// graphData.nodesByKey.put(entryNode.getKey(), entryNode);
	}

	public NodeType getExitPoint(long hash) {
		return exitNodes.get(hash);
	}

	public NodeType getExitPoint(ClusterNode.LookupKey key) {
		return exitNodesByKey.get(key);
	}

	public void addClusterExitNode(NodeType exitNode) {
		exitNodes.put(exitNode.getHash(), exitNode);
		exitNodesByKey.put(exitNode.getKey(), exitNode);
	}

	public int getExecutableNodeCount() {
		return executableNodeCount;
	}

	public void addNode(NodeType node) {
		graphData.nodesByHash.add(node);

		if (graphData.nodesByKey.put(node.getKey(), node) == null) {
			switch (node.getType()) {
				case NORMAL:
				case RETURN:
				case TRAMPOLINE:
				case PROCESS_ENTRY:
				case PROCESS_EXIT:
					// case MODULE_BOUNDARY:
				case SIGNAL_HANDLER:
				case SIGRETURN:
					executableNodeCount++;

					graphs.get(node.getModule().unit).incrementExecutableBlockCount();
			}
		}

		switch (node.getType()) {
			case CLUSTER_ENTRY:
				addClusterEntryNode(node);
				break;
			case CLUSTER_EXIT:
				addClusterExitNode(node);
				break;
		}
	}

	public void findUnreachableNodes() {
		unreachableNodes.clear();
		unreachableNodes.addAll(graphData.nodesByKey.values());
		Set<Node> visitedNodes = new HashSet<Node>();
		Queue<Node> bfsQueue = new LinkedList<Node>();
		bfsQueue.addAll(entryNodes.values());

		while (bfsQueue.size() > 0) {
			Node<?> node = bfsQueue.remove();
			unreachableNodes.remove(node);
			visitedNodes.add(node);

			for (Edge<? extends Node> edge : node.getOutgoingEdges()) {
				Node neighbor = edge.getToNode();
				if (!visitedNodes.contains(neighbor)) {
					bfsQueue.add(neighbor);
					visitedNodes.add(neighbor);
				}
			}

			Edge<? extends Node> continuationEdge = node.getCallContinuation();
			if (continuationEdge != null) {
				Node continuation = continuationEdge.getToNode();
				if (!visitedNodes.contains(continuation)) {
					bfsQueue.add(continuation);
					visitedNodes.add(continuation);
				}
			}
		}

		/**
		 * <pre>
		System.out.println(unreachableNodes.size() + " unreachable nodes for graph "
				+ graphData.containingGraph.dataSource);

		Set<Node> internalUnreachables = new HashSet<Node>();
		for (Node node : unreachableNodes) {
			for (Edge<Node> edge : node.getIncomingEdges()) {
				if (unreachableNodes.contains(edge.getFromNode())) {
					internalUnreachables.add(node);
					break;
				}
			}
		}
		 */
	}
}
