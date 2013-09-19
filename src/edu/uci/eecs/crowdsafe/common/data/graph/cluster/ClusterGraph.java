package edu.uci.eecs.crowdsafe.common.data.graph.cluster;

import java.util.EnumSet;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.datasource.cluster.ClusterTraceStreamType;

public class ClusterGraph extends ModuleGraphCluster<ClusterNode> {

	public static EnumSet<ClusterTraceStreamType> CLUSTER_GRAPH_STREAM_TYPES = EnumSet
			.allOf(ClusterTraceStreamType.class);

	public final ClusterModuleList moduleList;

	public ClusterGraph(AutonomousSoftwareDistribution cluster) {
		super(cluster);
		moduleList = new ClusterModuleList();
	}

	public ClusterGraph(AutonomousSoftwareDistribution cluster, ClusterModuleList moduleList) {
		super(cluster);
		this.moduleList = moduleList;

		for (ClusterModule module : moduleList.getModules()) {
			addModule(new ModuleGraph(module.unit, module.version));
		}
	}

	public ClusterNode addNode(long hash, SoftwareModule module, int relativeTag, MetaNodeType type) {
		ClusterModule mergedModule = moduleList.establishModule(module.unit, module.version);
		if (getModuleGraph(module.unit) == null)
			addModule(new ModuleGraph(module.unit, module.version));

		ClusterNode.Key key = new ClusterNode.Key(mergedModule, relativeTag, 0);
		while (graphData.nodesByKey.containsKey(key))
			key = new ClusterNode.Key(mergedModule, relativeTag, key.instanceId + 1);

		ClusterNode node = new ClusterNode(key, hash, type);
		addNode(node);
		return node;
	}

	public Graph.Node summarizeProcess() {
		return null;
	}
}
