package edu.uci.eecs.crowdsafe.common.data;

public enum DataMessageType {
	HASH_MERGE_RESULTS(0),
	TAG_MERGE_RESULTS(1),
	PROCESS_GRAPH(2);

	public final int id;

	private DataMessageType(int id) {
		this.id = id;
	}
}
