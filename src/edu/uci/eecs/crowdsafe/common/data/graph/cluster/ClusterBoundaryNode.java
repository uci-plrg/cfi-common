package edu.uci.eecs.crowdsafe.common.data.graph.cluster;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;

public class ClusterBoundaryNode extends ClusterNode<ClusterBoundaryNode.Key> {

	public static final ClusterModule BOUNDARY_MODULE = new ClusterModule(0, SoftwareDistributionUnit.CLUSTER_BOUNDARY,
			"");

	public static class Key implements Node.Key {
		private final long hash;

		private final MetaNodeType type;

		private final boolean isCallback;

		Key(long hash, MetaNodeType type, boolean isCallback) {
			this.hash = hash;
			this.type = type;
			this.isCallback = isCallback;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (hash ^ (hash >>> 32));
			result = prime * result + (isCallback ? 1231 : 1237);
			result = prime * result + ((type == null) ? 0 : type.hashCode());
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
			if (hash != other.hash)
				return false;
			if (isCallback != other.isCallback)
				return false;
			if (type != other.type)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return String.format("%s(0x%x)", type, hash);
		}
	}

	public ClusterBoundaryNode(long hash, MetaNodeType type) {
		this(hash, type, false);
	}

	public ClusterBoundaryNode(long hash, MetaNodeType type, boolean isCallback) {
		super(new Key(hash, type, isCallback));

		if ((type != MetaNodeType.CLUSTER_ENTRY) && (type != MetaNodeType.CLUSTER_EXIT))
			throw new IllegalArgumentException(String.format(
					"Cluster boundary node must have type %s or %s. Given type is %s.", MetaNodeType.CLUSTER_ENTRY,
					MetaNodeType.CLUSTER_EXIT, type));
	}

	@Override
	public ClusterModule getModule() {
		return BOUNDARY_MODULE;
	}

	@Override
	public int getRelativeTag() {
		return 0;
	}

	@Override
	public int getInstanceId() {
		return 0;
	}

	@Override
	public long getHash() {
		return key.hash;
	}

	@Override
	public MetaNodeType getType() {
		return key.type;
	}
	
	@Override
	public boolean isCallback() {
		return key.isCallback;
	}

	public String identify() {
		switch (key.type) {
			case CLUSTER_ENTRY:
				return String.format("ClusterEntry(0x%x)", key.hash);
			case CLUSTER_EXIT:
				return String.format("ClusterExit(0x%x)", key.hash);
			default:
				throw new IllegalStateException(String.format("%s must be of type %s or %s",
						getClass().getSimpleName(), MetaNodeType.CLUSTER_ENTRY, MetaNodeType.CLUSTER_EXIT));
		}
	}

	@Override
	public String toString() {
		return identify();
	}
}
