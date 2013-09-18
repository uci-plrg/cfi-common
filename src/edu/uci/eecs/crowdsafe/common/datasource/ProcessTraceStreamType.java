package edu.uci.eecs.crowdsafe.common.datasource;

public enum ProcessTraceStreamType {
	BLOCK_HASH("block-hash", 1),
	PAIR_HASH("pair-hash", 1),
	GRAPH_EDGE("graph-edge", 2),
	CROSS_MODULE_EDGE("cross-module", 3),
	GRAPH_NODE("graph-node", 2),
	MODULE("module");

	public final String id;
	public final int entryWordCount;

	private ProcessTraceStreamType(String id) {
		this(id, -1);
	}

	private ProcessTraceStreamType(String id, int entryWordCount) {
		this.id = id;
		this.entryWordCount = entryWordCount;
	}
}
