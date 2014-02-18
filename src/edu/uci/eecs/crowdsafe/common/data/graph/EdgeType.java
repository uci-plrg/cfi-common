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
	GENCODE_PERM("GP"),
	GENCODE_WRITE("GW");

	public final String code;

	private EdgeType(String code) {
		this.code = code;
	}

	public boolean isHighOrdinal(int ordinal) {
		return (ordinal > 1);
	}

	public boolean isContinuation() {
		return (this == CALL_CONTINUATION) || (this == EXCEPTION_CONTINUATION);
	}

	public int getClusterEntryOrdinal() {
		switch (this) {
			case INDIRECT:
				return 0;
			case DIRECT:
				return 1;
			case UNEXPECTED_RETURN:
				return 2;
			case GENCODE_PERM:
				return 3;
			case GENCODE_WRITE:
				return 4;
			default:
				throw new IllegalArgumentException(String.format("Edges of type %s cannot be a cluster entry edge!",
						this));
		}
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
			case GENCODE_PERM:
				return Graph.EdgeType.GENCODE_PERM;
			case GENCODE_WRITE:
				return Graph.EdgeType.GENCODE_WRITE;
		}
		throw new IllegalStateException("Unknown EdgeType " + this);
	}
}
