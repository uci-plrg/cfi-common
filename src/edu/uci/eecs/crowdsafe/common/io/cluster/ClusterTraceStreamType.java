package edu.uci.eecs.crowdsafe.common.io.cluster;

public enum ClusterTraceStreamType {
	GRAPH_NODE("graph-node", "dat"),
	GRAPH_EDGE("graph-edge", "dat"),
	MODULE("module", "log");

	public final String id;
	public final String extension;

	private ClusterTraceStreamType(String id, String extension) {
		this.id = id;
		this.extension = extension;
	}
}
