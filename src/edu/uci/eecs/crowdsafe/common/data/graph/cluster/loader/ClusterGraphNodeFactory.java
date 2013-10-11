package edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.data.graph.GraphLoadEventListener;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBasicBlock;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModule;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModuleList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ClusterGraphNodeFactory {

	private static final int ENTRY_BYTE_COUNT = 0x10;

	private final ClusterModuleList modules;
	private final LittleEndianInputStream input;

	private final GraphLoadEventListener listener;

	ClusterGraphNodeFactory(ClusterModuleList modules, LittleEndianInputStream input, GraphLoadEventListener listener) {
		this.input = input;
		this.modules = modules;
		this.listener = listener;
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
			System.out.println(String.format("Parse fail: %x", first));

		MetaNodeType type = MetaNodeType.values()[(int) ((first >> 0x30) & 0xffL)];
		ClusterModule module = modules.getModule(moduleIndex);

		long hash = input.readLong();

		ClusterNode<?> node = null;

		switch (type) {
			case CLUSTER_ENTRY:
			case CLUSTER_EXIT:
				node = new ClusterBoundaryNode(hash, type);
				break;
			default:
				node = new ClusterBasicBlock(module, relativeTag, instanceId, hash, type);
		}

		if (listener != null)
			listener.nodeCreation(node);

		return node;
	}

	void close() throws IOException {
		if (input.ready())
			Log.log("Warning: input stream %s has %d bytes remaining.", input.description, input.available());

		input.close();
	}
}
