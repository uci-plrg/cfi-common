package edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import edu.uci.eecs.crowdsafe.common.data.results.Graph;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ClusterMetadata {

	private boolean isMain = false;
	private ClusterMetadataSequence rootSequence;
	public final Map<UUID, ClusterMetadataSequence> sequences = new HashMap<UUID, ClusterMetadataSequence>();

	public void mergeSequence(ClusterMetadataSequence newSequence) {
		if (newSequence.executions.isEmpty())
			return;

		ClusterMetadataSequence existingSequence = sequences.get(newSequence.id);
		if (existingSequence == null) {
			if (newSequence.isRoot()) {
				if (rootSequence == null) {
					rootSequence = newSequence;
				} else {
					newSequence.setRoot(false);
				}
			}
			sequences.put(newSequence.id, newSequence);
			// } else if (!newSequence.equals(existingSequence)) {
			// throw new IllegalArgumentException("Attempt to merge a different version of an existing sequence!");
		}
	}

	public ClusterMetadataSequence getRootSequence() {
		return rootSequence;
	}

	public boolean isMain() {
		return isMain;
	}

	public void setMain(boolean isMain) {
		this.isMain = isMain;
	}

	public boolean isEmpty() {
		return sequences.isEmpty();
	}

	public boolean isSingletonExecution() {
		return (sequences.size() == 1) && (rootSequence.executions.size() == 1);
	}

	public ClusterMetadataExecution getSingletonExecution() {
		return rootSequence.executions.get(0);
	}

	public Graph.ProcessMetadataHistory summarizeIntervals() {
		Graph.ProcessMetadataHistory.Builder metadataHistoryBuilder = Graph.ProcessMetadataHistory.newBuilder();
		Graph.ProcessMetadataSequence.Builder metadataSequenceBuilder = Graph.ProcessMetadataSequence.newBuilder();
		Graph.ProcessMetadata.Builder metadataBuilder = Graph.ProcessMetadata.newBuilder();
		Graph.IntervalGroup.Builder intervalGroupBuilder = Graph.IntervalGroup.newBuilder();
		Graph.Interval.Builder intervalBuilder = Graph.Interval.newBuilder();

		int i = 0;
		for (ClusterMetadataSequence sequence : sequences.values()) {
			metadataSequenceBuilder.setIsRoot(sequence.isRoot());
			for (ClusterMetadataExecution execution : sequence.executions) {

				if (isMain)
					Log.log("Writing execution %s into result metadata sequence %d", execution.id, i++);

				metadataBuilder.setIdHigh(execution.id.getMostSignificantBits());
				metadataBuilder.setIdLow(execution.id.getLeastSignificantBits());
				for (EvaluationType type : EvaluationType.values()) {
					intervalGroupBuilder.setType(type.getResultType());
					for (ClusterUIBInterval interval : execution.getIntervals(type)) {
						intervalBuilder.setSpan(interval.span);
						intervalBuilder.setOccurences(interval.count);
						intervalBuilder.setMaxConsecutive(interval.maxConsecutive);
						intervalGroupBuilder.addInterval(intervalBuilder.build());
						intervalBuilder.clear();
					}
					metadataBuilder.addIntervalGroup(intervalGroupBuilder.build());
					intervalGroupBuilder.clear();
				}
				metadataSequenceBuilder.addExecution(metadataBuilder.build());
				metadataBuilder.clear();
			}
			metadataHistoryBuilder.addSequence(metadataSequenceBuilder.build());
			metadataSequenceBuilder.clear();
		}

		return metadataHistoryBuilder.build();
	}
}
