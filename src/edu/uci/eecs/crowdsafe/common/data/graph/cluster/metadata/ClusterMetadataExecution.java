package edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClusterMetadataExecution {

	public final UUID id;
	private final Map<EvaluationType, List<ClusterUIBInterval>> intervals = new EnumMap<EvaluationType, List<ClusterUIBInterval>>(
			EvaluationType.class);
	public final List<ClusterUIB> uibs = new ArrayList<ClusterUIB>();

	public ClusterMetadataExecution() {
		this(UUID.randomUUID());
	}
	
	public int getIntervalCount() {
		int count = 0;
		for (List<ClusterUIBInterval> group : intervals.values()) {
			count += group.size();
		}
		return count;
	}

	public List<ClusterUIBInterval> getIntervals(EvaluationType type) {
		if (intervals.isEmpty())
			return Collections.emptyList();

		return intervals.get(type);
	}

	public void addInterval(ClusterUIBInterval interval) {
		if (intervals.isEmpty())
			for (EvaluationType type : EvaluationType.values())
				intervals.put(type, new ArrayList<ClusterUIBInterval>());

		intervals.get(interval.type).add(interval);
	}

	public ClusterMetadataExecution(UUID id) {
		this.id = id;
	}
}
