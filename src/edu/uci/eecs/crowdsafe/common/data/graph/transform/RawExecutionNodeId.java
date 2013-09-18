package edu.uci.eecs.crowdsafe.common.data.graph.transform;

public class RawExecutionNodeId {

	final int tag;
	final int version;

	public RawExecutionNodeId(int tag, int version) {
		this.tag = tag;
		this.version = version;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + tag;
		result = prime * result + version;
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
		RawExecutionNodeId other = (RawExecutionNodeId) obj;
		if (tag != other.tag)
			return false;
		if (version != other.version)
			return false;
		return true;
	}
}
