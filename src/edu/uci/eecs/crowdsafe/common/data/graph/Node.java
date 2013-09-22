package edu.uci.eecs.crowdsafe.common.data.graph;


public abstract class Node<EdgeEndpointType extends Node<EdgeEndpointType>> implements NodeIdentifier,
		NodeList<EdgeEndpointType> {

	public interface Key {
	}

	protected final EdgeSet<EdgeEndpointType> edges = new EdgeSet<EdgeEndpointType>();

	public abstract Key getKey();

	public boolean isModuleRelativeEquivalent(Node<?> other) {
		return getKey().equals(other.getKey()) && (getType() == other.getType()) && (getHash() == other.getHash());
	}

	public boolean isModuleRelativeMismatch(Node<?> other) {
		return !(getKey().equals(other.getKey()) && (getType() == other.getType()) && (getHash() == other.getHash()));
	}

	public boolean hasIncomingEdges() {
		return edges.getEdgeCount(EdgeSet.Direction.INCOMING) > 0;
	}

	public OrdinalEdgeList<EdgeEndpointType> getIncomingEdges() {
		return edges.getEdges(EdgeSet.Direction.INCOMING);
	}

	/**
	 * Includes the call continuation when present
	 */
	public boolean hasOutgoingEdges() {
		return edges.getEdgeCount(EdgeSet.Direction.OUTGOING) > 0;
	}

	public int getOutgoingOrdinalCount() {
		return edges.getOrdinalCount(EdgeSet.Direction.OUTGOING);
	}

	/**
	 * Includes the call continuation when present
	 */
	public OrdinalEdgeList<EdgeEndpointType> getOutgoingEdges() {
		return edges.getEdges(EdgeSet.Direction.OUTGOING);
	}

	/**
	 * Includes the call continuation when present
	 */
	public OrdinalEdgeList<EdgeEndpointType> getOutgoingEdges(int ordinal) {
		return edges.getEdges(EdgeSet.Direction.OUTGOING, ordinal);
	}

	/**
	 * @return null for non-call node, edge for the first block of the calling procedure
	 */
	public Edge<EdgeEndpointType> getCallContinuation() {
		return edges.getCallContinuation();
	}

	public Edge<EdgeEndpointType> getOutgoingEdge(EdgeEndpointType toNode) {
		OrdinalEdgeList<EdgeEndpointType> edgeList = edges.getEdges(EdgeSet.Direction.OUTGOING);
		try {
			for (Edge<EdgeEndpointType> edge : edgeList) {
				if (edge.getToNode().getKey().equals(toNode.getKey()))
					return edge;
			}
		} finally {
			edgeList.release();
		}
		return null;
	}

	public Edge<EdgeEndpointType> getOutgoingEdge(EdgeEndpointType toNode, int ordinal) {
		for (Edge<EdgeEndpointType> edge : edges.getEdges(EdgeSet.Direction.OUTGOING, ordinal)) {
			if (edge.getToNode().getKey().equals(toNode.getKey()))
				return edge;
		}
		return null;
	}

	public boolean hasCompatibleEdges(Node<?> other) {
		return edges.checkOutgoingEdgeCompatibility(other.edges);
	}

	public boolean isMetaNode() {
		return (getType() != MetaNodeType.NORMAL) && (getType() != MetaNodeType.RETURN);
	}

	@Override
	@SuppressWarnings("unchecked")
	public EdgeEndpointType get(int index) {
		return (EdgeEndpointType) this;
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + getKey().hashCode();
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
		Node<?> other = (Node<?>) obj;
		return getKey().equals(other.getKey());
	}
}
