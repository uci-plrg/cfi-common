package edu.uci.eecs.crowdsafe.common.data.graph;

import edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer.ClusterDataWriter;

public class Edge<EndpointType extends Node<?>> implements ClusterDataWriter.Edge<EndpointType> {
	private final EndpointType toNode;
	private EdgeType edgeType;
	private int ordinal;

	// Add this filed for debugging reason, cause it provides
	// more information when debugging
	private final EndpointType fromNode;

	public Edge(EndpointType fromNode, EndpointType toNode, EdgeType edgeType, int ordinal) {
		if (fromNode == null)
			throw new IllegalArgumentException("Edge construction is missing the 'from' node!");
		if (toNode == null)
			throw new IllegalArgumentException("Edge construction is missing the 'to' node!");

		this.fromNode = fromNode;
		this.toNode = toNode;
		this.edgeType = edgeType;
		this.ordinal = ordinal;

		if (toNode.getType() == MetaNodeType.CLUSTER_ENTRY)
			toNode.toString();

		if (fromNode.getRelativeTag() == 0x1ea8eL)
			fromNode.toString();
	}

	public void setEdgeType(EdgeType edgeType) {
		this.edgeType = edgeType;
	}

	@Override
	public EndpointType getFromNode() {
		return fromNode;
	}

	@Override
	public EndpointType getToNode() {
		return toNode;
	}

	@Override
	public EdgeType getEdgeType() {
		return edgeType;
	}

	@Override
	public int getOrdinal() {
		return ordinal;
	}

	public boolean isModuleRelativeEquivalent(Edge<?> other) {
		if (other == null)
			return false;
		if (!fromNode.isModuleRelativeEquivalent(other.fromNode))
			return false;
		if (!toNode.isModuleRelativeEquivalent(other.toNode))
			return false;
		if (edgeType != other.edgeType)
			return false;
		if (ordinal != other.ordinal)
			return false;

		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((edgeType == null) ? 0 : edgeType.hashCode());
		result = prime * result + ((fromNode == null) ? 0 : fromNode.hashCode());
		result = prime * result + ordinal;
		result = prime * result + ((toNode == null) ? 0 : toNode.hashCode());
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
		Edge<?> other = (Edge<?>) obj;
		if (edgeType != other.edgeType)
			return false;
		if (!fromNode.equals(other.fromNode))
			return false;
		if (ordinal != other.ordinal)
			return false;
		if (!toNode.equals(other.toNode))
			return false;
		return true;
	}

	public String toString() {
		return String.format("%s(%s)--%d-->%s", fromNode, edgeType, ordinal, toNode);
	}
}
