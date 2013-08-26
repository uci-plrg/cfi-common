package edu.uci.eecs.crowdsafe.common.data.graph;

import java.util.List;

import edu.uci.eecs.crowdsafe.common.data.graph.EdgeSet.Direction;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;

public abstract class Node<NodeType extends Node> implements NodeList {

	public interface Key {
	}

	protected final EdgeSet<NodeType> edges = new EdgeSet<NodeType>();

	public abstract Key getKey();

	public abstract long getHash();

	public abstract MetaNodeType getType();

	public boolean hasIncomingEdges() {
		return edges.getEdgeCount(EdgeSet.Direction.INCOMING) > 0;
	}

	public List<Edge<NodeType>> getIncomingEdges() {
		return edges.getEdges(EdgeSet.Direction.INCOMING);
	}

	public boolean hasOutgoingEdges() {
		return edges.getEdgeCount(EdgeSet.Direction.OUTGOING) > 0;
	}

	public int getOutgoingOrdinalCount() {
		return edges.getOrdinalCount(EdgeSet.Direction.OUTGOING);
	}

	public List<Edge<NodeType>> getOutgoingEdges() {
		return edges.getEdges(EdgeSet.Direction.OUTGOING);
	}

	public List<Edge<NodeType>> getOutgoingEdges(int ordinal) {
		return edges.getEdges(EdgeSet.Direction.OUTGOING, ordinal);
	}

	/**
	 * @return null for non-call node, edge for the first block of the calling procedure
	 */
	public Edge<NodeType> getCallContinuation() {
		return edges.getCallContinuation();
	}

	public Edge<NodeType> getOutgoingEdge(NodeType toNode) {
		for (Edge<NodeType> edge : edges.getEdges(EdgeSet.Direction.OUTGOING)) {
			if (edge.getToNode().getKey().equals(toNode.getKey()))
				return edge;
		}
		return null;
	}

	public Edge<NodeType> getOutgoingEdge(NodeType toNode, int ordinal) {
		for (Edge<NodeType> edge : edges.getEdges(EdgeSet.Direction.OUTGOING, ordinal)) {
			if (edge.getToNode().getKey().equals(toNode.getKey()))
				return edge;
		}
		return null;
	}

	public boolean hasCompatibleEdges(Node other) {
		return edges.checkOutgoingEdgeCompatibility(other.edges);
	}

	// put the edge impl here?

	public boolean isMetaNode() {
		return getType() != MetaNodeType.NORMAL;
	}

	@Override
	public Node get(int index) {
		return this;
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
		Node other = (Node) obj;
		return getKey().equals(other.getKey());
	}
}
