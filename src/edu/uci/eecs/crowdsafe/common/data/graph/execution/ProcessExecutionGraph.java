package edu.uci.eecs.crowdsafe.common.data.graph.execution;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.CrowdSafeCollections;
import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDataSource;

/**
 * <p>
 * This class abstracts the binary-level labeled control flow graph of any execution of a binary executable.
 * </p>
 * 
 * <p>
 * There are a few assumptions: 1. Within one execution, the tag, which is address of the block of code in the code
 * cache of DynamoRIO, can uniquely represents an actual block of code in run-time memory. This might not be true if the
 * same address has different pieces of code at different time. 2. In windows, we already have a list of known core
 * utility DLL's, which means we will match modules according to the module names plus its version number. This might
 * not be a universally true assumption, but it's still reasonable at this point. We will treat unknown modules as
 * inline code, which is part of the main graph.
 * </p>
 * 
 * <p>
 * This class will have a list of its subclass, ModuleGraph, which is the graph representation of each run-time module.
 * </p>
 * 
 * <p>
 * This class should have the signature2Node filed which maps the signature hash to the bogus signature node. The basic
 * matching strategy separates the main module and all other kernel modules. All these separate graphs have a list of
 * callbacks or export functions from other modules, which have a corresponding signature hash. For those nodes, we try
 * to match them according to their signature hash.
 * </p>
 * 
 * @author peizhaoo
 * 
 */

public class ProcessExecutionGraph {

	private static class ModuleGraphSorter implements Comparator<ModuleGraph> {
		static final ModuleGraphSorter INSTANCE = new ModuleGraphSorter();

		@Override
		public int compare(ModuleGraph first, ModuleGraph second) {
			return second.getExecutableBlockCount() - first.getExecutableBlockCount();
		}
	}

	// Represents the list of core modules
	private final Map<AutonomousSoftwareDistribution, ModuleGraphCluster> moduleGraphs = new HashMap<AutonomousSoftwareDistribution, ModuleGraphCluster>();
	private final Map<SoftwareDistributionUnit, ModuleGraphCluster> moduleGraphsBySoftwareUnit = new HashMap<SoftwareDistributionUnit, ModuleGraphCluster>();

	// Used to normalize the tag in a single graph
	protected final ProcessExecutionModuleSet modules;

	public final ProcessTraceDataSource dataSource;

	public ProcessExecutionGraph(ProcessTraceDataSource dataSource, ProcessExecutionModuleSet modules) {
		this.dataSource = dataSource;
		this.modules = modules;

		for (AutonomousSoftwareDistribution dist : ConfiguredSoftwareDistributions.getInstance().distributions.values()) {
			ModuleGraphCluster moduleCluster = new ModuleGraphCluster(dist, this);
			moduleGraphs.put(dist, moduleCluster);

			for (SoftwareDistributionUnit unit : dist.distributionUnits) {
				moduleGraphsBySoftwareUnit.put(unit, moduleCluster);
			}
		}
	}

	public ProcessExecutionModuleSet getModules() {
		return modules;
	}

	public ModuleGraphCluster getModuleGraphCluster(AutonomousSoftwareDistribution distribution) {
		return moduleGraphs.get(distribution);
	}

	public ModuleGraphCluster getModuleGraphCluster(SoftwareDistributionUnit softwareUnit) {
		ModuleGraphCluster cluster = moduleGraphsBySoftwareUnit.get(softwareUnit);
		if (cluster != null)
			return cluster;
		return moduleGraphs.get(ConfiguredSoftwareDistributions.getInstance().distributions
				.get(ConfiguredSoftwareDistributions.MAIN_PROGRAM));
	}

	public Collection<ModuleGraphCluster> getAutonomousClusters() {
		return moduleGraphs.values();
	}

	public int calculateTotalNodeCount() {
		int count = 0;
		for (ModuleGraphCluster cluster : moduleGraphs.values()) {
			count += cluster.graphData.nodesByKey.size();
		}
		return count;
	}

	public Graph.Process summarizeProcess() {
		Graph.Process.Builder processBuilder = Graph.Process.newBuilder();
		processBuilder.setId(dataSource.getProcessId());
		processBuilder.setName(dataSource.getProcessName());

		for (AutonomousSoftwareDistribution dist : moduleGraphs.keySet()) {
			Graph.Cluster.Builder clusterBuilder = Graph.Cluster.newBuilder();
			Graph.Module.Builder moduleBuilder = Graph.Module.newBuilder();
			Graph.ModuleInstance.Builder moduleInstanceBuilder = Graph.ModuleInstance.newBuilder();
			Graph.UnreachableNode.Builder unreachableBuilder = Graph.UnreachableNode.newBuilder();
			Graph.Node.Builder nodeBuilder = Graph.Node.newBuilder();
			Graph.Edge.Builder edgeBuilder = Graph.Edge.newBuilder();

			ModuleGraphCluster cluster = moduleGraphs.get(dist);
			int clusterNodeCount = cluster.getGraphData().nodesByKey.size();

			clusterBuilder.setDistributionName(dist.name);
			clusterBuilder.setNodeCount(clusterNodeCount);
			clusterBuilder.setExecutableNodeCount(cluster.getExecutableNodeCount());
			clusterBuilder.setEntryPointCount(cluster.getEntryNodeCount());

			for (ModuleGraph moduleGraph : CrowdSafeCollections.createSortedCopy(cluster.getGraphs(),
					ModuleGraphSorter.INSTANCE)) {
				moduleBuilder.clear().setName(moduleGraph.softwareUnit.filename);
				moduleBuilder.setVersion(moduleGraph.version);
				moduleInstanceBuilder.setModule(moduleBuilder.build());
				moduleInstanceBuilder.setNodeCount(moduleGraph.getExecutableBlockCount());
				clusterBuilder.addModule(moduleInstanceBuilder.build());
			}

			Set<ExecutionNode> unreachableNodes = cluster.getUnreachableNodes();
			if (!unreachableNodes.isEmpty()) {
				for (ExecutionNode unreachableNode : unreachableNodes) {
					moduleBuilder.setName(unreachableNode.getModule().unit.filename);
					moduleBuilder.setVersion(unreachableNode.getModule().version);
					nodeBuilder.clear().setModule(moduleBuilder.build());
					nodeBuilder.setRelativeTag((int) unreachableNode.getRelativeTag());
					nodeBuilder.setTagVersion(unreachableNode.getTagVersion());
					nodeBuilder.setHashcode(unreachableNode.getHash());
					unreachableBuilder.clear().setNode(nodeBuilder.build());
					unreachableBuilder.setIsEntryPoint(true);

					if (!unreachableNode.getIncomingEdges().isEmpty()) {
						for (Edge<ExecutionNode> incoming : unreachableNode.getIncomingEdges()) {
							if (unreachableNodes.contains(incoming.getFromNode())) {
								unreachableBuilder.setIsEntryPoint(false);
							} else {
								moduleBuilder.setName(incoming.getFromNode().getModule().unit.filename);
								moduleBuilder.setVersion(incoming.getFromNode().getModule().version);
								nodeBuilder.setModule(moduleBuilder.build());
								edgeBuilder.clear().setFromNode(nodeBuilder.build());
								edgeBuilder.setToNode(unreachableBuilder.getNode());
								edgeBuilder.setType(incoming.getEdgeType().mapToResultType());
								unreachableBuilder.addMissedIncomingEdge(edgeBuilder.build());
							}
						}
					}
					
					clusterBuilder.addUnreachable(unreachableBuilder.build());
				}
			}

			processBuilder.addCluster(clusterBuilder.build());
		}

		return processBuilder.build();
	}

	public String toString() {
		return dataSource.getProcessName() + "-" + dataSource.getProcessId();
	}
}