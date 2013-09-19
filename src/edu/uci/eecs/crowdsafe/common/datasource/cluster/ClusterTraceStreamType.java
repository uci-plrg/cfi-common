package edu.uci.eecs.crowdsafe.common.datasource.cluster;

public enum ClusterTraceStreamType {
	GRAPH_NODE("graph-node", "dat", 2),
	GRAPH_EDGE("graph-edge", "dat", 1),
	MODULE("module", "log");

	public final String id;
	public final String extension;
	public final int entryWordCount;

	private ClusterTraceStreamType(String id, String extension) {
		this(id, extension, -1);
	}

	private ClusterTraceStreamType(String id, String extension, int entryWordCount) {
		this.id = id;
		this.extension = extension;
		this.entryWordCount = entryWordCount;
	}
}
