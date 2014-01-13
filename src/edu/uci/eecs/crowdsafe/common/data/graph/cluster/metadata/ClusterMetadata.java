package edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClusterMetadata {

	private ClusterMetadataSequence rootSequence;
	public final Map<UUID, ClusterMetadataSequence> sequences = new HashMap<UUID, ClusterMetadataSequence>();

	public void mergeSequence(ClusterMetadataSequence newSequence) {
		if (newSequence.executions.isEmpty())
			return;

		ClusterMetadataSequence existingSequence = sequences.get(newSequence.id);
		if (existingSequence == null) {
			if (newSequence.isRoot()) {
				if (rootSequence == null)
					rootSequence = newSequence;
				else
					newSequence.setRoot(false);
			}
			sequences.put(newSequence.id, newSequence);
		} else if (!newSequence.equals(existingSequence)) {
			throw new IllegalArgumentException("Attempt to merge a different version of an existing sequence!");
		}
	}

	public ClusterMetadataSequence getRootSequence() {
		return rootSequence;
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
}
