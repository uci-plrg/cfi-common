package edu.uci.eecs.crowdsafe.common.data.graph.cluster;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeSet;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;

public abstract class ClusterNode<KeyType extends Node.Key> extends Node<ClusterNode<?>> {

	final KeyType key;

	protected ClusterNode(KeyType key) {
		this.key = key;
	}
	
	public abstract int getInstanceId();
	
	public abstract ClusterModule getModule();

	@Override
	public KeyType getKey() {
		return key;
	}

	public void addIncomingEdge(Edge<ClusterNode<?>> e) {
		edges.addEdge(EdgeSet.Direction.INCOMING, e);
	}

	public void addOutgoingEdge(Edge<ClusterNode<?>> e) {
		edges.addEdge(EdgeSet.Direction.OUTGOING, e);
	}

	@Override
	public int hashCode() {
		return key.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ClusterNode) {
			return key.equals(((ClusterNode<?>) o).key);
		}
		return false;
	}
}
