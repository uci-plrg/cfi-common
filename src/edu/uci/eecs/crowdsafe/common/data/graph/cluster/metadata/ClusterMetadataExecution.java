package edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClusterMetadataExecution {

	public final UUID id;
	public final List<ClusterUIBInterval> intervals = new ArrayList<ClusterUIBInterval>();
	public final List<ClusterUIB> uibs = new ArrayList<ClusterUIB>();

	public ClusterMetadataExecution() {
		this(UUID.randomUUID());
	}

	public ClusterMetadataExecution(UUID id) {
		this.id = id;
	}
}
