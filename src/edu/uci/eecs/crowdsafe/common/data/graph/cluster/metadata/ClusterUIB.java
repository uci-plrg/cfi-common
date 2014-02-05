package edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;

public class ClusterUIB {

	public final Edge<ClusterNode<?>> edge;
	public final boolean isAdmitted;
	public final int traversalCount;
	public final int instanceCount;

	public ClusterUIB(Edge<ClusterNode<?>> uib, boolean isAdmitted, int traversalCount, int instanceCount) {
		this.edge = uib;
		this.isAdmitted = isAdmitted;
		this.traversalCount = traversalCount;
		this.instanceCount = instanceCount;
	}
}
