package edu.uci.eecs.crowdsafe.common.data.graph.transform;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;
import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModule;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModuleList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessModuleLoader;
import edu.uci.eecs.crowdsafe.common.datasource.ClusterGraphStreamType;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDirectory;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeTraceUtil;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;

public class RawGraphTransformer {

	private class ClusterOutput {
		final LittleEndianOutputStream nodeStream;
		final LittleEndianOutputStream edgeStream;
		final BufferedWriter moduleWriter;

		ClusterOutput(AutonomousSoftwareDistribution distribution) throws IOException {
			String outputFilename = String.format("%s.%s.%s.%s", dataSource.getProcessName(), distribution.id,
					ClusterGraphStreamType.GRAPH_NODE.id, ClusterGraphStreamType.GRAPH_NODE.extension);
			File outputFile = new File(runDir, outputFilename);
			nodeStream = new LittleEndianOutputStream(outputFile);

			outputFilename = String.format("%s.%s.%s.%s", dataSource.getProcessName(), distribution.id,
					ClusterGraphStreamType.GRAPH_EDGE.id, ClusterGraphStreamType.GRAPH_EDGE.extension);
			outputFile = new File(runDir, outputFilename);
			edgeStream = new LittleEndianOutputStream(outputFile);

			outputFilename = String.format("%s.%s.%s.%s", dataSource.getProcessName(), distribution.id,
					ClusterGraphStreamType.MODULE.id, ClusterGraphStreamType.MODULE.extension);
			outputFile = new File(runDir, outputFilename);
			moduleWriter = new BufferedWriter(new FileWriter(outputFile));
		}

		void writeNode(ClusterNode node) throws IOException {
			long word = node.getKey().module.id;
			word |= ((long) node.getKey().relativeTag) << 0x10;
			word |= ((long) node.getKey().instanceId) << 0x28;
			word |= ((long) node.getType().ordinal()) << 0x30;
			nodeStream.writeLong(word);
			nodeStream.writeLong(node.getHash());
		}

		void writeEdge(ClusterNode fromNode, ClusterNode toNode, EdgeType type, int ordinal) throws IOException {
			long word = fromNode.getKey().module.id;
			word |= ((long) fromNode.getKey().relativeTag) << 0x10;
			word |= ((long) fromNode.getKey().instanceId) << 0x28;
			word |= ((long) type.ordinal()) << 0x30;
			word |= ((long) ordinal) << 0x38;
			edgeStream.writeLong(word);

			word = toNode.getKey().module.id;
			word |= ((long) toNode.getKey().relativeTag) << 0x10;
			word |= ((long) toNode.getKey().instanceId) << 0x28;
			edgeStream.writeLong(word);
		}

		void writeModules(ClusterModuleList modules) throws IOException {
			for (ClusterModule module : modules.sortById()) {
				moduleWriter.write(String.format("%s-%s\n", module.unit.name, module.version));
			}
		}

		void flush() throws IOException {
			nodeStream.flush();
			nodeStream.close();
			edgeStream.flush();
			edgeStream.close();
			moduleWriter.flush();
			moduleWriter.close();
		}
	}

	private final ArgumentStack args;

	private final ProcessModuleLoader executionModuleLoader = new ProcessModuleLoader();

	// transitory per run:

	private File runDir = null;
	private ProcessTraceDataSource dataSource = null;
	private ProcessExecutionModuleSet executionModules = null;
	private final Map<AutonomousSoftwareDistribution, ClusterModuleList> clusterModules = new HashMap<AutonomousSoftwareDistribution, ClusterModuleList>();
	private final Map<AutonomousSoftwareDistribution, ClusterOutput> outputsByCluster = new HashMap<AutonomousSoftwareDistribution, ClusterOutput>();

	private final Map<RawExecutionNodeId, ClusterNode> nodesByRawId = new HashMap<RawExecutionNodeId, ClusterNode>();
	private final Map<ClusterNode.Key, ClusterNode> nodesByKey = new HashMap<ClusterNode.Key, ClusterNode>();

	public RawGraphTransformer(ArgumentStack args) {
		this.args = args;

		OptionArgumentMap.populateOptions(args);
	}

	private void run() {
		try {
			Log.addOutput(System.out);
			CrowdSafeConfiguration.initialize(EnumSet.of(CrowdSafeConfiguration.Environment.CROWD_SAFE_COMMON_DIR));
			ConfiguredSoftwareDistributions.initialize();

			while (args.size() > 0) {
				runDir = new File(args.pop());
				dataSource = new ProcessTraceDirectory(runDir, ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES);
				transformGraph(dataSource);

				runDir = null;
				dataSource = null;
				executionModules = null;
				clusterModules.clear();
				outputsByCluster.clear();
				nodesByRawId.clear();
				nodesByKey.clear();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void transformGraph(ProcessTraceDataSource dataSource) throws IOException {
		executionModules = executionModuleLoader.loadModules(dataSource);
		for (AutonomousSoftwareDistribution dist : ConfiguredSoftwareDistributions.getInstance().distributions.values()) {
			clusterModules.put(dist, new ClusterModuleList());
		}

		transformNodes(dataSource.getLittleEndianInputStream(ProcessTraceStreamType.GRAPH_NODE));
		transformEdges(dataSource.getLittleEndianInputStream(ProcessTraceStreamType.GRAPH_EDGE));
		transformCrossModuleEdges(dataSource.getLittleEndianInputStream(ProcessTraceStreamType.CROSS_MODULE_EDGE));
		writeNodes();
		writeModules();
		flushOutputs();
	}

	private void transformNodes(LittleEndianInputStream input) throws IOException {
		RawGraphEntry.TwoWordFactory factory = new RawGraphEntry.TwoWordFactory(input);

		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.TwoWordEntry nodeEntry = factory.createEntry();
			entryIndex++;

			long absoluteTag = CrowdSafeTraceUtil.getTag(nodeEntry.first);
			int tagVersion = CrowdSafeTraceUtil.getTagVersion(nodeEntry.first);
			MetaNodeType nodeType = CrowdSafeTraceUtil.getNodeMetaType(nodeEntry.first);
			RawExecutionNodeId rawNodeId = new RawExecutionNodeId((int) absoluteTag, tagVersion);

			ModuleInstance moduleInstance = executionModules.getModuleForLoadedBlock(absoluteTag, entryIndex);
			int relativeTag = (int) (absoluteTag - moduleInstance.start);

			AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(moduleInstance.unit);
			ClusterModule clusterModule = clusterModules.get(cluster).establishModule(moduleInstance.unit,
					moduleInstance.version);
			establishOutputs(cluster);

			ClusterNode node = new ClusterNode(clusterModule, relativeTag, tagVersion, nodeEntry.second, nodeType);
			nodesByRawId.put(rawNodeId, node);
			nodesByKey.put(node.getKey(), node);
		}
	}

	private void transformEdges(LittleEndianInputStream input) throws IOException {
		RawGraphEntry.TwoWordFactory factory = new RawGraphEntry.TwoWordFactory(input);

		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.TwoWordEntry edgeEntry = factory.createEntry();
			entryIndex++;

			long absoluteFromTag = CrowdSafeTraceUtil.getTag(edgeEntry.first);
			int fromTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.first);
			RawExecutionNodeId rawFromId = new RawExecutionNodeId((int) absoluteFromTag, fromTagVersion);
			ClusterNode fromNode = nodesByRawId.get(rawFromId);
			EdgeType type = CrowdSafeTraceUtil.getTagEdgeType(edgeEntry.first);
			int ordinal = CrowdSafeTraceUtil.getEdgeOrdinal(edgeEntry.first);

			long absoluteToTag = CrowdSafeTraceUtil.getTag(edgeEntry.second);
			int toTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.second);
			RawExecutionNodeId rawToId = new RawExecutionNodeId((int) absoluteToTag, toTagVersion);
			ClusterNode toNode = nodesByRawId.get(rawToId);

			if (fromNode == null) {
				Log.log("Error: missing 'from' node %s", rawFromId);
				continue;
			}
			if (toNode == null) {
				Log.log("Error: missing 'to' node %s", rawToId);
				continue;
			}

			AutonomousSoftwareDistribution fromCluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(fromNode.getKey().module.unit);
			AutonomousSoftwareDistribution toCluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(toNode.getKey().module.unit);
			if (fromCluster == toCluster) {
				outputsByCluster.get(fromCluster).writeEdge(fromNode, toNode, type, ordinal);
			} else {
				throw new IllegalStateException(String.format(
						"Intra-module edge from %s to %s crosses a cluster boundary!", fromNode, toNode));
			}
		}
	}

	private void transformCrossModuleEdges(LittleEndianInputStream input) throws IOException {
		RawGraphEntry.ThreeWordFactory factory = new RawGraphEntry.ThreeWordFactory(input);

		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.ThreeWordEntry edgeEntry = factory.createEntry();
			entryIndex++;

			long absoluteFromTag = CrowdSafeTraceUtil.getTag(edgeEntry.first);
			int fromTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.first);
			RawExecutionNodeId rawFromId = new RawExecutionNodeId((int) absoluteFromTag, fromTagVersion);
			ClusterNode fromNode = nodesByRawId.get(rawFromId);
			EdgeType type = CrowdSafeTraceUtil.getTagEdgeType(edgeEntry.first);
			int ordinal = CrowdSafeTraceUtil.getEdgeOrdinal(edgeEntry.first);

			long absoluteToTag = CrowdSafeTraceUtil.getTag(edgeEntry.second);
			int toTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.second);
			RawExecutionNodeId rawToId = new RawExecutionNodeId((int) absoluteToTag, toTagVersion);
			ClusterNode toNode = nodesByRawId.get(rawToId);

			AutonomousSoftwareDistribution fromCluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(fromNode.getKey().module.unit);
			AutonomousSoftwareDistribution toCluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(toNode.getKey().module.unit);
			if (fromCluster == toCluster) {
				outputsByCluster.get(fromCluster).writeEdge(fromNode, toNode, type, ordinal);
			} else {
				ClusterNode entry = new ClusterNode(toNode.getKey().module, toNode.getKey().relativeTag,
						toNode.getKey().instanceId, edgeEntry.third, MetaNodeType.CLUSTER_ENTRY);
				nodesByKey.put(entry.getKey(), entry);
				outputsByCluster.get(toCluster).writeEdge(entry, toNode, EdgeType.CLUSTER_ENTRY, 0);

				ClusterNode exit = new ClusterNode(fromNode.getKey().module, fromNode.getKey().relativeTag,
						fromNode.getKey().instanceId, edgeEntry.third, MetaNodeType.CLUSTER_EXIT);
				nodesByKey.put(exit.getKey(), exit);
				outputsByCluster.get(fromCluster).writeEdge(fromNode, exit, type, ordinal);
			}
		}
	}

	private void establishOutputs(AutonomousSoftwareDistribution distribution) throws IOException {
		ClusterOutput outputs = outputsByCluster.get(distribution);
		if (outputs == null) {
			outputs = new ClusterOutput(distribution);
			outputsByCluster.put(distribution, outputs);
		}
	}

	private void writeNodes() throws IOException {
		for (ClusterNode node : nodesByKey.values()) {
			AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(node.getKey().module.unit);
			outputsByCluster.get(cluster).writeNode(node);
		}
	}

	private void writeModules() throws IOException {
		for (Map.Entry<AutonomousSoftwareDistribution, ClusterModuleList> entry : clusterModules.entrySet()) {
			ClusterOutput output = outputsByCluster.get(entry.getKey());
			if (output != null)
				output.writeModules(entry.getValue());
		}
	}

	private void flushOutputs() throws IOException {
		for (ClusterOutput output : outputsByCluster.values()) {
			output.flush();
		}
	}

	private RawGraphEntry.Writer<?> createWriter(LittleEndianOutputStream output, int recordSize) {
		switch (recordSize) {
			case 2:
				return new RawGraphEntry.TwoWordWriter(output);
			case 3:
				return new RawGraphEntry.ThreeWordWriter(output);
			default:
				throw new IllegalArgumentException(String.format("The %s only supports records of size 2 or 3!",
						getClass().getSimpleName()));
		}
	}

	// this can be used to remove duplicates in block-hash and pair-hash files
	/**
	 * <pre>
	private void packRawFile(String filename) {
		Set<RawGraphEntry> records = new HashSet<RawGraphEntry>();

		LittleEndianInputStream input = new LittleEndianInputStream(new File(filename));
		RawGraphEntry.Factory factory = createFactory(input);

		while (factory.hasMoreEntries()) {
			records.add(factory.createEntry());
		}

		File outputFile = new File(filename + ".pack");
		LittleEndianOutputStream output = new LittleEndianOutputStream(outputFile);
		RawGraphEntry.Writer writer = createWriter(output);
		for (RawGraphEntry record : records) {
			writer.writeRecord(record);
		}
		writer.flush();
		records.clear();
	}
	 */

	public static void main(String[] args) {
		RawGraphTransformer packer = new RawGraphTransformer(new ArgumentStack(args));
		packer.run();
	}
}
