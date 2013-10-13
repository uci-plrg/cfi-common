package edu.uci.eecs.crowdsafe.common.data.graph;

public enum MetaNodeType {
	NORMAL,
	CONTEXT_ENTRY,
	CONTEXT_EXIT,
	TRAMPOLINE,
	RETURN,
	SIGNAL_HANDLER,
	SIGRETURN,
	CLUSTER_ENTRY,
	// MODULE_BOUNDARY,
	CLUSTER_EXIT
}
