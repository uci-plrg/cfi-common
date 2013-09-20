package edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBasicBlock;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModule;
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

	ClusterNode<?> createNode() throws IOException {
		long first = input.readLong();
		int moduleIndex = (int) (first & 0xffffL);
		long relativeTag = ((first >> 0x10) & 0xffffffL);
		int instanceId = (int) ((first >> 0x28) & 0xffL);

		if (((int) ((first >> 0x30) & 0xffL)) > MetaNodeType.values().length)
			toString();

		MetaNodeType type = MetaNodeType.values()[(int) ((first >> 0x30) & 0xffL)];
		ClusterModule module = modules.getModule(moduleIndex);

		long hash = input.readLong();

		switch (type) {
			case CLUSTER_ENTRY:
			case CLUSTER_EXIT:
				return new ClusterBoundaryNode(hash, type);
		}

		return new ClusterBasicBlock(module, relativeTag, instanceId, hash, type);
	}

	void close() throws IOException {
		input.close();
	}
}
