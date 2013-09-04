package edu.uci.eecs.crowdsafe.common.data.graph;

import edu.uci.eecs.crowdsafe.common.data.results.Graph;

public enum EdgeType {
	INDIRECT,
	DIRECT,
	CALL_CONTINUATION,
	UNEXPECTED_RETURN,
	MODULE_ENTRY,
	CROSS_CUSTOM_MODULE;

	public Graph.EdgeType mapToResultType() {
		switch (this) {
			case CALL_CONTINUATION:
				return Graph.EdgeType.CALL_CONTINUATION;
			case DIRECT:
				return Graph.EdgeType.CALL_CONTINUATION;
			case INDIRECT:
				return Graph.EdgeType.CALL_CONTINUATION;
			case MODULE_ENTRY:
				return Graph.EdgeType.CALL_CONTINUATION;
			case UNEXPECTED_RETURN:
				return Graph.EdgeType.CALL_CONTINUATION;
		}
		throw new IllegalStateException("Unknown EdgeType " + this);
	}
}
