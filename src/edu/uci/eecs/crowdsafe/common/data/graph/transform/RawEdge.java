package edu.uci.eecs.crowdsafe.common.data.graph.transform;

import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer.ClusterDataWriter;

public class RawEdge implements ClusterDataWriter.Edge<IndexedClusterNode> {
	private IndexedClusterNode fromNode;
	private IndexedClusterNode toNode;
	public final EdgeType type;
	public final int ordinal;

	RawEdge(IndexedClusterNode fromNode, IndexedClusterNode toNode, EdgeType type, int ordinal) {
		this.fromNode = fromNode;
		this.toNode = toNode;
		this.type = type;
		this.ordinal = ordinal;
	}

	@Override
	public IndexedClusterNode getFromNode() {
		return fromNode;
	}

	@Override
	public IndexedClusterNode getToNode() {
		return toNode;
	}

	@Override
	public EdgeType getEdgeType() {
		return type;
	}

	@Override
	public int getOrdinal() {
		return ordinal;
	}

	public void setFromNode(IndexedClusterNode fromNode) {
		this.fromNode = fromNode;
	}

	public void setToNode(IndexedClusterNode toNode) {
		this.toNode = toNode;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + fromNode.hashCode();
		result = prime * result + ordinal;
		result = prime * result + toNode.hashCode();
		result = prime * result + type.hashCode();
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
		RawEdge other = (RawEdge) obj;
		if (!fromNode.equals(other.fromNode))
			return false;
		if (ordinal != other.ordinal)
			return false;
		if (!toNode.equals(other.toNode))
			return false;
		if (type != other.type)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("%s(%s)--%d-->%s", fromNode, type, ordinal, toNode);
	}
}
