package edu.uci.eecs.crowdsafe.common.data.graph.execution.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.common.exception.InvalidTagException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeTraceUtil;

/**
 * <p>
 * The format of the lookup file is as the following:
 * </p>
 * <p>
 * Each entry consists of 8-byte tag + 8-byte hash.
 * </p>
 * <p>
 * 8-byte tag: 1-byte version number | 1-byte node type | 6-byte tag
 * </p>
 * 
 * 
 * @param lookupFiles
 * @return
 * @throws InvalidTagException
 */
public class ProcessGraphNodeFactory {
	private final ProcessExecutionModuleSet modules;
	private final LittleEndianInputStream input;

	long tag = 0, tagOriginal = 0, hash = 0;
	long blockIndex = -1L;
	ModuleInstance module;
	ModuleGraphCluster moduleCluster;

	public ProcessGraphNodeFactory(ProcessExecutionModuleSet modules, LittleEndianInputStream input) throws IOException {
		this.modules = modules;
		this.input = input;
	}

	boolean ready() throws IOException {
		return input.ready(0x10);
	}

	ExecutionNode createNode() throws IOException {
		tagOriginal = input.readLong();
		hash = input.readLong();
		blockIndex++;

		tag = CrowdSafeTraceUtil.getTagEffectiveValue(tagOriginal);
		int versionNumber = CrowdSafeTraceUtil.getNodeVersionNumber(tagOriginal);
		MetaNodeType metaNodeType = CrowdSafeTraceUtil.getNodeMetaType(tagOriginal);
		module = modules.getModule(tag, blockIndex, ProcessTraceStreamType.GRAPH_NODE);

		return new ExecutionNode(module, metaNodeType, tag, versionNumber, hash, blockIndex);
	}

	void close() throws IOException {
		if (input.ready())
			Log.log("Warning: input stream %s has %d bytes remaining.", input.description, input.available());

		input.close();
	}
}
