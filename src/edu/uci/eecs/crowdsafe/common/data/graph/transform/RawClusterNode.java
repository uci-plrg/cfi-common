package edu.uci.eecs.crowdsafe.common.data.graph.transform;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;

public class RawClusterNode {

	public final AutonomousSoftwareDistribution cluster;
	public final ClusterNode<?> node;
	public final int index;

	RawClusterNode(AutonomousSoftwareDistribution cluster, ClusterNode<?> node, int index) {
		this.cluster = cluster;
		this.node = node;
		this.index = index;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cluster.name == null) ? 0 : cluster.name.hashCode());
		result = prime * result + index;
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
		RawClusterNode other = (RawClusterNode) obj;
		if (!cluster.name.equals(other.cluster.name))
			return false;
		if (index != other.index)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return node.toString();
	}
}
