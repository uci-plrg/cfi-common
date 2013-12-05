package edu.uci.eecs.crowdsafe.common.data.graph;

import edu.uci.eecs.crowdsafe.common.data.results.Graph;

public enum EdgeType {
	INDIRECT("I"),
	DIRECT("D"),
	CALL_CONTINUATION("CC") {
		@Override
		public boolean isHighOrdinal(int ordinal) {
			return (ordinal > 2);
		}
	},
	EXCEPTION_CONTINUATION("EC") {
		@Override
		public boolean isHighOrdinal(int ordinal) {
			return (ordinal > 3);
		}
	},
	UNEXPECTED_RETURN("UR"),
	CLUSTER_ENTRY("E");

	public final String code;

	private EdgeType(String code) {
		this.code = code;
	}

	public boolean isHighOrdinal(int ordinal) {
		return (ordinal > 1);
	}

	public Graph.EdgeType mapToResultType() {
		switch (this) {
			case INDIRECT:
				return Graph.EdgeType.INDIRECT;
			case DIRECT:
				return Graph.EdgeType.DIRECT;
			case CALL_CONTINUATION:
				return Graph.EdgeType.CALL_CONTINUATION;
			case EXCEPTION_CONTINUATION:
				return Graph.EdgeType.EXCEPTION_CONTINUATION;
			case UNEXPECTED_RETURN:
				return Graph.EdgeType.UNEXPECTED_RETURN;
			case CLUSTER_ENTRY:
				return Graph.EdgeType.MODULE_ENTRY;
		}
		throw new IllegalStateException("Unknown EdgeType " + this);
	}
}
