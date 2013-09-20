package edu.uci.eecs.crowdsafe.common.data.graph.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;

public class RawClusterNodeList implements Iterable<RawClusterNode> {

	private final Map<ClusterNode.Key, RawClusterNode> nodesByKey = new HashMap<ClusterNode.Key, RawClusterNode>();
	private final Map<Long, RawClusterNode> entryPointHashes = new HashMap<Long, RawClusterNode>();
	private final Map<Long, RawClusterNode> exitPointHashes = new HashMap<Long, RawClusterNode>();

	private final List<RawClusterNode> nodeList = new ArrayList<RawClusterNode>();

	RawClusterNode addNode(AutonomousSoftwareDistribution cluster, ClusterNode<?> node) {
		RawClusterNode existing = null;
		switch (node.getType()) {
			case CLUSTER_ENTRY:
				existing = entryPointHashes.get(node.getHash());
				break;
			case CLUSTER_EXIT:
				existing = exitPointHashes.get(node.getHash());
				break;
			default:
				existing = nodesByKey.get(node.getKey());
		}
		if (existing != null)
			return existing;

		RawClusterNode rawNode = new RawClusterNode(cluster, node, nodeList.size());
		nodeList.add(rawNode);
		switch (node.getType()) {
			case CLUSTER_ENTRY:
				entryPointHashes.put(node.getHash(), rawNode);
				break;
			case CLUSTER_EXIT:
				exitPointHashes.put(node.getHash(), rawNode);
				break;
			default:
				nodesByKey.put(node.getKey(), rawNode);
		}
		return rawNode;
	}

	RawClusterNode getNode(ClusterNode.Key key) {
		return nodesByKey.get(key);
	}

	int size() {
		return nodesByKey.size();
	}

	@Override
	public Iterator<RawClusterNode> iterator() {
		return nodeList.iterator();
	}
}
