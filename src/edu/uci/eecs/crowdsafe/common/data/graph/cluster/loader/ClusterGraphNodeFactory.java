package edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModuleList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;

public class ClusterGraphNodeFactory {

	private static final int ENTRY_BYTE_COUNT = 0x10;
	
	private final ClusterModuleList modules;
	private final LittleEndianInputStream input;

	ClusterGraphNodeFactory(ClusterModuleList modules, LittleEndianInputStream input) {
		this.input = input;
		this.modules = modules;
	}

	boolean ready() throws IOException {
		return input.ready(ENTRY_BYTE_COUNT);
	}

	ClusterNode createNode() throws IOException {
		return null;
	}

	void close() throws IOException {
		input.close();
	}
}
