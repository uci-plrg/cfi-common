package edu.uci.eecs.crowdsafe.common.data.graph.execution;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.w3c.dom.Node;

import edu.uci.eecs.crowdsafe.common.CrowdSafeCollections;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ProcessExecutionGraphSummary {

	private static class ModuleGraphSorter implements Comparator<ModuleGraph> {
		static final ModuleGraphSorter INSTANCE = new ModuleGraphSorter();

		@Override
		public int compare(ModuleGraph first, ModuleGraph second) {
			return second.getExecutableBlockCount()
					- first.getExecutableBlockCount();
		}
	}

	/**
	 * This function is called when the graph is constructed. It can contain any kind of analysis of the current
	 * ExecutionGraph and output it the the console.
	 * 
	 * It could actually contains information like: 1. Number of nodes & signature nodes in the main module and in every
	 * module. 2. Number of non-kernel modules in the main module.
	 */
	public static void summarizeGraph(ProcessExecutionGraph graph) {
		for (ModuleGraphCluster cluster : graph.getAutonomousClusters()) {
			int clusterNodeCount = cluster.getGraphData().nodesByKey.size();
			Set<ExecutionNode> unreachableNodes = cluster.getUnreachableNodes();

			Log.log("Cluster %s has %d nodes (%d executable, %d entry points)",
					cluster.distribution.name, clusterNodeCount,
					cluster.getExecutableNodeCount(),
					cluster.getEntryNodeCount());

			for (ModuleGraph moduleGraph : CrowdSafeCollections
					.createSortedCopy(cluster.getGraphs(),
							ModuleGraphSorter.INSTANCE)) {
				Log.log("\tModule %s: %d nodes",
						moduleGraph.softwareUnit.filename,
						moduleGraph.getExecutableBlockCount());
			}

			if (!unreachableNodes.isEmpty()) {
				Log.log("Warning: found %d unreachable nodes!",
						unreachableNodes.size());
				// for (ExecutionNode unreachableNode : unreachableNodes) {
				// Log.log("\t%s", unreachableNode.toString());
				// }
				for (ExecutionNode unreachableNode : unreachableNodes) {
					if (unreachableNode.getIncomingEdges().isEmpty()) {
						Log.log("\t%s has no incoming edges", unreachableNode);
					} else {
						for (Edge<ExecutionNode> incoming : unreachableNode
								.getIncomingEdges()) {
							if (!unreachableNodes.contains(incoming
									.getFromNode()))
								Log.log("\tMissed entry point %s", incoming);
						}
					}
				}
			}
		}
	}
}
