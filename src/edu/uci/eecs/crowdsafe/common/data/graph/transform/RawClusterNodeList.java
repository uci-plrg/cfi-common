package edu.uci.eecs.crowdsafe.common.data.graph.transform;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;

public class RawClusterNodeList {
	private final Map<ClusterNode.Key, RawClusterNodeId> nodesByKey = new HashMap<ClusterNode.Key, RawClusterNodeId>();

	RawClusterNodeId addNode(AutonomousSoftwareDistribution cluster, ClusterNode node) {
		RawClusterNodeId id = nodesByKey.get(node.getKey());
		if (id == null) {
			id = new RawClusterNodeId(cluster, node, nodesByKey.size());
			nodesByKey.put(node.getKey(), id);
		}
		return id;
	}

	RawClusterNodeId getNode(ClusterNode.Key key) {
		return nodesByKey.get(key);
	}
	
	int size() {
		return nodesByKey.size();
	}
	
	Collection<RawClusterNodeId> values() {
		return nodesByKey.values();
	}
}
