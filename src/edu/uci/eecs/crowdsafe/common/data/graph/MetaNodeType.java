package edu.uci.eecs.crowdsafe.common.data.graph;

public enum MetaNodeType {
	NORMAL,
	PROCESS_ENTRY,
	PROCESS_EXIT,
	TRAMPOLINE,
	RETURN,
	SIGNAL_HANDLER,
	SIGRETURN,
	CLUSTER_ENTRY,
	// MODULE_BOUNDARY,
	CLUSTER_EXIT
}
