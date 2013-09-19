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
import edu.uci.eecs.crowdsafe.common.data.graph.Node;

public class ModuleGraphCluster<NodeType extends Node> {
	public final AutonomousSoftwareDistribution cluster;

	// Maps from signature hash to entry and exit points
	protected Map<Long, NodeType> entryNodesBySignatureHash = new HashMap<Long, NodeType>();
	protected Map<Long, NodeType> exitNodesBySignatureHash = new HashMap<Long, NodeType>();

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

	public int getEntryNodeCount() {
		return entryNodesBySignatureHash.size();
	}

	public Map<Long, NodeType> getEntryPoints() {
		return entryNodesBySignatureHash;
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
	}

	public void addClusterEntryNode(NodeType entryNode) {
		if (entryNodesBySignatureHash.put(entryNode.getHash(), entryNode) == null)
			graphData.nodesByKey.put(entryNode.getKey(), entryNode);
	}

	public void findUnreachableNodes() {
		unreachableNodes.clear();
		unreachableNodes.addAll(graphData.nodesByKey.values());
		Set<Node> visitedNodes = new HashSet<Node>();
		Queue<Node> bfsQueue = new LinkedList<Node>();
		bfsQueue.addAll(entryNodesBySignatureHash.values());

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
