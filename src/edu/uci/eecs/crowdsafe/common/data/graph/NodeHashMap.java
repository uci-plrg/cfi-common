package edu.uci.eecs.crowdsafe.common.data.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class NodeHashMap<NodeType extends Node<NodeType>> {
	private final Map<Long, NodeList<NodeType>> map = new HashMap<Long, NodeList<NodeType>>();

	private int nodeCount = 0;

	@SuppressWarnings("unchecked")
	public void add(NodeType node) {
		NodeList<NodeType> existing = map.get(node.getHash());
		if (existing == null) {
			map.put(node.getHash(), node);
		} else {
			if (existing.equals(node))
				return;

			if (existing.isSingleton()) {
				NodeArrayList<NodeType> list = new NodeArrayList<NodeType>();
				if (list.contains(node))
					return;

				list.add((NodeType) existing);
				list.add(node);
				map.put(node.getHash(), list);
			} else {
				NodeArrayList<NodeType> list = (NodeArrayList<NodeType>) existing;
				if (list.contains(node))
					return;

				list.add(node);
			}
		}

		nodeCount++;
	}

	public NodeList<NodeType> get(long hash) {
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
