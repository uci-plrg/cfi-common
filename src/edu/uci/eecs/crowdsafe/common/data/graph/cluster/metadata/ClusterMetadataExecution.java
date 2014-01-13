package edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClusterMetadataExecution {

	public final UUID id;
	public final Map<ClusterUIBInterval.Type, List<ClusterUIBInterval>> intervals = new EnumMap<ClusterUIBInterval.Type, List<ClusterUIBInterval>>(
			ClusterUIBInterval.Type.class);
	public final List<ClusterUIB> uibs = new ArrayList<ClusterUIB>();

	public ClusterMetadataExecution() {
		this(UUID.randomUUID());
	}

	public List<ClusterUIBInterval> getIntervals(ClusterUIBInterval.Type type) {
		if (intervals.isEmpty())
			return Collections.emptyList();

		return intervals.get(type);
	}

	public void addInterval(ClusterUIBInterval interval) {
		if (intervals.isEmpty())
			for (ClusterUIBInterval.Type type : ClusterUIBInterval.Type.values())
				intervals.put(type, new ArrayList<ClusterUIBInterval>());

		intervals.get(interval.type).add(interval);
	}

	public ClusterMetadataExecution(UUID id) {
		this.id = id;
	}
}
