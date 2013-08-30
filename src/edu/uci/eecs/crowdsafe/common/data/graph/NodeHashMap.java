package edu.uci.eecs.crowdsafe.common.data.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;

public class NodeHashMap {
	private final Map<Long, NodeList> map = new HashMap<Long, NodeList>();

	private int nodeCount = 0;

	public void add(Node node) {
		NodeList existing = map.get(node.getHash());
		if (existing == null) {
			map.put(node.getHash(), node);
		} else {
			if (existing.equals(node))
				return;

			if (existing.isSingleton()) {
				NodeArrayList list = new NodeArrayList();
				if (list.contains(node))
					return;
				
				list.add((Node) existing);
				list.add(node);
				map.put(node.getHash(), list);
			} else {
				NodeArrayList list = (NodeArrayList) existing;
				if (list.contains(node))
					return;
				
				list.add(node);
			}
		}

		nodeCount++;
	}

	public NodeList get(long hash) {
		return map.get(hash);
	}

	public Set<Long> keySet() {
		return map.keySet();
	}

	public int getHashCount() {
		return map.size();
	}

	public int getNodeCount() {
		return nodeCount;
	}
}
