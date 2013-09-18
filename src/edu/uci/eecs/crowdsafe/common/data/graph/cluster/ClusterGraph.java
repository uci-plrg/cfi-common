package edu.uci.eecs.crowdsafe.common.data.graph.cluster;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.common.datasource.ClusterGraphStreamType;

public class ClusterGraph {
	
	public static EnumSet<ClusterGraphStreamType> CLUSTER_GRAPH_STREAM_TYPES = EnumSet.allOf(ClusterGraphStreamType.class);

	public final NodeHashMap nodesByHash = new NodeHashMap();
	
	public final Map<ClusterNode.Key, ClusterNode> nodesByKey = new HashMap<ClusterNode.Key, ClusterNode>();
	
	public final ClusterModuleList moduleList = new ClusterModuleList();

	public ClusterNode addNode(long hash, SoftwareModule module, int relativeTag, MetaNodeType type) {
		ClusterModule mergedModule = moduleList.addModule(module.unit, module.version);
		
		ClusterNode.Key key = new ClusterNode.Key(mergedModule, relativeTag, 0);
		while (nodesByKey.containsKey(key))
			key = new ClusterNode.Key(mergedModule, relativeTag, key.instanceId+1);
		
		ClusterNode node = new ClusterNode(key, hash, type);
		nodesByHash.add(node);
		return node;
	}
}
