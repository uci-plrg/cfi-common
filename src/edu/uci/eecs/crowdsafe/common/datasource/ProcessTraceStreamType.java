package edu.uci.eecs.crowdsafe.common.datasource;

public enum ProcessTraceStreamType {
	BLOCK_HASH("block-hash"),
	PAIR_HASH("pair-hash"),
	MODULE_GRAPH("graph-edge"),
	CROSS_MODULE_GRAPH("cross-module"),
	GRAPH_HASH("graph-node"),
	MODULE("module");

	public final String id;

	private ProcessTraceStreamType(String id) {
		this.id = id;
	}
}
