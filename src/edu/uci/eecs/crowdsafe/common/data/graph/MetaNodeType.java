package edu.uci.eecs.crowdsafe.common.data.graph;

public enum MetaNodeType {
	NORMAL(true),
	CONTEXT_ENTRY,
	CONTEXT_EXIT,
	TRAMPOLINE(true),
	RETURN(true),
	SIGNAL_HANDLER(true),
	SIGRETURN(true),
	CLUSTER_ENTRY,
	// MODULE_BOUNDARY,
	CLUSTER_EXIT;

	public final boolean isExecutable;

	private MetaNodeType() {
		this(false);
	}

	private MetaNodeType(boolean isExecutable) {
		this.isExecutable = isExecutable;
	}
}
