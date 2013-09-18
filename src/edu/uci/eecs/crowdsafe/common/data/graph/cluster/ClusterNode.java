package edu.uci.eecs.crowdsafe.common.data.graph.cluster;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeSet;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;

public class ClusterNode extends Node<ClusterNode> {
	public static class Key implements Node.Key {
		public final ClusterModule module;

		public final int relativeTag;

		public final int instanceId;

		Key(ClusterModule module, int relativeTag, int instanceId) {
			this.module = module;
			this.relativeTag = relativeTag;
			this.instanceId = instanceId;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + instanceId;
			result = prime * result + ((module == null) ? 0 : module.hashCode());
			result = prime * result + relativeTag;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Key other = (Key) obj;
			if (instanceId != other.instanceId)
				return false;
			if (module == null) {
				if (other.module != null)
					return false;
			} else if (!module.equals(other.module))
				return false;
			if (relativeTag != other.relativeTag)
				return false;
			return true;
		}
	}

	private final Key key;

	public final long hash;

	private final MetaNodeType type;

	ClusterNode(Key key, long hash, MetaNodeType type) {
		this.key = key;
		this.hash = hash;
		this.type = type;
	}

	public ClusterNode(ClusterModule module, int relativeTag, int instanceId, long hash, MetaNodeType type) {
		this(new Key(module, relativeTag, instanceId), hash, type);
	}

	@Override
	public ClusterNode.Key getKey() {
		return key;
	}

	@Override
	public ClusterModule getModule() {
		return key.module;
	}

	@Override
	public int getRelativeTag() {
		return key.relativeTag;
	}

	public MetaNodeType getType() {
		return type;
	}

	@Override
	public long getHash() {
		return hash;
	}

	public void addIncomingEdge(Edge<ClusterNode> e) {
		edges.addEdge(EdgeSet.Direction.INCOMING, e);
	}

	public void addOutgoingEdge(Edge<ClusterNode> e) {
		edges.addEdge(EdgeSet.Direction.OUTGOING, e);
	}
}
