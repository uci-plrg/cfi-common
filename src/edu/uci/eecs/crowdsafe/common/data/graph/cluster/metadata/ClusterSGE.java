package edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;

public class ClusterSGE {

	public final Edge<ClusterNode<?>> edge;
	public final int uibCount;
	public final int suibCount;

	public ClusterSGE(Edge<ClusterNode<?>> edge, int uibCount, int suibCount) {
		this.edge = edge;
		this.uibCount = uibCount;
		this.suibCount = suibCount;
	}
}
