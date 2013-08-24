package edu.uci.eecs.crowdsafe.common.data.graph.execution;

import java.util.Comparator;

import edu.uci.eecs.crowdsafe.common.CrowdSafeCollections;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ProcessExecutionGraphSummary {

	private static class ModuleGraphSorter implements Comparator<ModuleGraph> {
		static final ModuleGraphSorter INSTANCE = new ModuleGraphSorter();

		@Override
		public int compare(ModuleGraph first, ModuleGraph second) {
			return second.getExecutableBlockCount() - first.getExecutableBlockCount();
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
			Log.log("Cluster %s has %d nodes (%d executable, %d entry points), with %d accessible nodes.",
					cluster.distribution.name, clusterNodeCount, cluster
							.getExecutableNodeCount(), cluster
							.getEntryNodeCount(), cluster
							.searchAccessibleNodes().size());

			for (ModuleGraph moduleGraph : CrowdSafeCollections
					.createSortedCopy(cluster.getGraphs(),
							ModuleGraphSorter.INSTANCE)) {
				Log.log("\tModule %s: %d nodes", moduleGraph.softwareUnit.filename,
						moduleGraph.getExecutableBlockCount());
			}
		}
	}
}