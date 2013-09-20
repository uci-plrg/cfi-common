package edu.uci.eecs.crowdsafe.common.data.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 * Warning: only the iterator is threadsafe. Concurrent modifications to the list will corrupt the data structure! 
 * 
 * TODO: not sure what happens in a unity merge when one graph instance is being merged to itself.
 */
public class EdgeSet<EdgeEndpointType extends Node<EdgeEndpointType>> {

	public enum Direction {
		INCOMING,
		OUTGOING;
	}

	public static class OutgoingOrdinal {
		public EdgeType type;
		private int position;
		private int size;

		OutgoingOrdinal(int position) {
			this.position = position;
			this.size = 0;
			this.type = null;
		}
	}

	/**
	 * Both outgoing and incoming edges are held in this list. The outgoing edges occur first and are indexed by
	 * outgoingOrdinals. The incoming edges start at directionDivider and are not sorted, grouped or indexed.
	 */
	final List<Edge<EdgeEndpointType>> edges = new ArrayList<Edge<EdgeEndpointType>>();
	final List<OutgoingOrdinal> outgoingOrdinals = new ArrayList<OutgoingOrdinal>();
	int directionDivider = 0;

	Edge<EdgeEndpointType> callContinuation;

	private final ThreadLocal<OrdinalEdgeList<EdgeEndpointType>> threadListView = new ThreadLocal<OrdinalEdgeList<EdgeEndpointType>>() {
		protected OrdinalEdgeList<EdgeEndpointType> initialValue() {
			return new OrdinalEdgeList<EdgeEndpointType>(EdgeSet.this);
		}
	};

	// 15% hot during load!
	public void addEdge(Direction direction, Edge<EdgeEndpointType> edge) {
		if ((direction == Direction.OUTGOING) && (edge.getEdgeType() == EdgeType.CALL_CONTINUATION)) {
			if (callContinuation != null) {
				if (!callContinuation.equals(edge)) {
					throw new IllegalStateException("Cannot add multiple call continuation edges!");
				}
			} else {
				callContinuation = edge;
			}
			return;
		}

		if (edges.contains(edge))
			return;

		OrdinalEdgeList<EdgeEndpointType> listView = threadListView.get();
		listView.modified = true;

		if (direction == Direction.INCOMING) {
			edges.add(edge);
			return;
		}

		int ordinal = edge.getOrdinal();
		int addCount = (ordinal + 1) - outgoingOrdinals.size();
		for (int i = 0; i < addCount; i++) {
			outgoingOrdinals.add(new OutgoingOrdinal(directionDivider));
		}

		OutgoingOrdinal group = outgoingOrdinals.get(ordinal);
		if (group.type == null) {
			group.type = edge.getEdgeType();
		} else if (group.type != edge.getEdgeType()) {
			throw new IllegalArgumentException(String.format(
					"Attempt to add an edge of type %s to an edge group of type %s!", edge.getEdgeType(), group.type));
		}
		edges.add(null);
		int edgePosition = group.position + group.size;
		for (int i = edges.size() - 1; i > edgePosition; i--) {
			edges.set(i, edges.get(i - 1));
		}
		edges.set(edgePosition, edge);
		directionDivider++;
		group.size++;
		for (int i = ordinal + 1; i < outgoingOrdinals.size(); i++) {
			outgoingOrdinals.get(i).position++;
		}
	}

	public List<Edge<EdgeEndpointType>> getEdges(Direction direction, int ordinal) {
		OrdinalEdgeList<EdgeEndpointType> listView = threadListView.get();
		switch (direction) {
			case INCOMING:
				throw new UnsupportedOperationException("Incoming edges are not grouped by ordinal.");
			case OUTGOING:
				if (ordinal >= outgoingOrdinals.size()) {
					listView.group = null;
					listView.start = 0;
					listView.end = 0;
					listView.includeCallContinuation = false;
					listView.modified = false;
				} else {
					listView.group = outgoingOrdinals.get(ordinal);
					listView.start = listView.group.position;
					listView.end = listView.group.position + listView.group.size;
					listView.includeCallContinuation = false;
					listView.modified = false;
				}
				break;
		}
		return listView;
	}

	public List<Edge<EdgeEndpointType>> getEdges(Direction direction) {
		OrdinalEdgeList<EdgeEndpointType> listView = threadListView.get();
		listView.includeCallContinuation = (callContinuation != null) && (direction == Direction.OUTGOING);
		listView.modified = false;
		if (edges.isEmpty()) {
			listView.start = 0;
			listView.group = null;
			listView.end = 0;
			return listView;
		}

		switch (direction) {
			case INCOMING:
				listView.group = null;
				listView.start = directionDivider;
				listView.end = edges.size();
				listView.modified = false;
				break;
			case OUTGOING:
				listView.group = null;
				listView.start = 0;
				listView.end = directionDivider;
				listView.modified = false;
				break;
		}
		return listView;
	}

	public Edge<EdgeEndpointType> getCallContinuation() {
		return callContinuation;
	}

	public int getOrdinalCount(Direction direction) {
		switch (direction) {
			case INCOMING:
				throw new UnsupportedOperationException("Incoming edges are not grouped by ordinal.");
			case OUTGOING:
				return outgoingOrdinals.size();
			default:
				throw new IllegalArgumentException(String.format("Unknown direction %s", direction));
		}
	}

	public int getEdgeCount(Direction direction) {
		switch (direction) {
			case INCOMING:
				return (edges.size() - directionDivider);
			case OUTGOING:
				return directionDivider;
			default:
				throw new IllegalStateException(String.format("Unknown direction %d", direction));
		}
	}

	public boolean checkOutgoingEdgeCompatibility(EdgeSet<?> other) {
		int max = Math.min(outgoingOrdinals.size(), other.outgoingOrdinals.size());
		for (int i = 0; i < max; i++) {
			OutgoingOrdinal myOrdinal = outgoingOrdinals.get(i);
			OutgoingOrdinal otherOrdinal = other.outgoingOrdinals.get(i);
			if ((myOrdinal.type == null) || (otherOrdinal.type == null))
				continue;
			if (myOrdinal.type != otherOrdinal.type)
				return false;
		}
		return true;
	}
}
