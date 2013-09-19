package edu.uci.eecs.crowdsafe.common.data.graph.cluster;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeSet;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;

public class ClusterNode extends Node<ClusterNode> {

	public static class Key implements Node.Key {
		public final ClusterModule module;

		public final long relativeTag;

		public final int instanceId;

		public Key(ClusterModule module, long relativeTag, int instanceId) {
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
			result = prime * result + (int) (relativeTag ^ (relativeTag >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (obj.getClass() == Key.class) {
				Key other = (Key) obj;
				if (instanceId != other.instanceId)
					return false;
				if (!module.equals(other.module))
					return false;
				if (relativeTag != other.relativeTag)
					return false;
				return true;
			} else if (obj.getClass() == LookupKey.class) {
				LookupKey other = (LookupKey) obj;
				if (instanceId != other.instanceId)
					return false;
				if (!module.equals(other.module))
					return false;
				if (relativeTag != other.relativeTag)
					return false;
				return true;
			} else {
				return false;
			}
		}
	}

	public static class LookupKey implements Node.Key {
		private ClusterModule module;

		private long relativeTag;

		private int instanceId;

		public LookupKey setIdentity(ClusterModule module, long relativeTag, int instanceId) {
			this.module = module;
			this.relativeTag = relativeTag;
			this.instanceId = instanceId;

			return this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + instanceId;
			result = prime * result + ((module == null) ? 0 : module.hashCode());
			result = prime * result + (int) (relativeTag ^ (relativeTag >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (obj.getClass() == Key.class) {
				Key other = (Key) obj;
				if (instanceId != other.instanceId)
					return false;
				if (!module.equals(other.module))
					return false;
				if (relativeTag != other.relativeTag)
					return false;
				return true;
			} else if (obj.getClass() == LookupKey.class) {
				LookupKey other = (LookupKey) obj;
				if (instanceId != other.instanceId)
					return false;
				if (!module.equals(other.module))
					return false;
				if (relativeTag != other.relativeTag)
					return false;
				return true;
			} else {
				return false;
			}
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

	public ClusterNode(ClusterModule module, long relativeTag, int instanceId, long hash, MetaNodeType type) {
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
		return (int) key.relativeTag;
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

	public String identify() {
		switch (type) {
			case CLUSTER_ENTRY:
				return String.format("ClusterEntry(0x%x)", hash);
			case CLUSTER_EXIT:
				return String.format("ClusterExit(0x%x)", hash);
			default:
				return String.format("%s(0x%x-i%d|0x%x)", key.module.unit.filename, key.relativeTag, key.instanceId,
						hash);
		}
	}

	@Override
	public int hashCode() {
		return key.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ClusterNode) {
			return key.equals(((ClusterNode) o).key);
		}
		return false;
	}

	@Override
	public String toString() {
		return identify();
	}
}
