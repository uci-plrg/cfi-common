package edu.uci.eecs.crowdsafe.common.data.graph;

import edu.uci.eecs.crowdsafe.common.data.results.Graph;

public enum EdgeType {
	INDIRECT("I"),
	DIRECT("D"),
	CALL_CONTINUATION("CC"),
	UNEXPECTED_RETURN("UR"),
	CLUSTER_ENTRY("E");

	public final String code;

	private EdgeType(String code) {
		this.code = code;
	}

	public Graph.EdgeType mapToResultType() {
		switch (this) {
			case CALL_CONTINUATION:
				return Graph.EdgeType.CALL_CONTINUATION;
			case DIRECT:
				return Graph.EdgeType.DIRECT;
			case INDIRECT:
				return Graph.EdgeType.INDIRECT;
			case CLUSTER_ENTRY:
				return Graph.EdgeType.MODULE_ENTRY;
			case UNEXPECTED_RETURN:
				return Graph.EdgeType.UNEXPECTED_RETURN;
		}
		throw new IllegalStateException("Unknown EdgeType " + this);
	}
}
