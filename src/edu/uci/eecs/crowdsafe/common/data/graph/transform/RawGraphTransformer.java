package edu.uci.eecs.crowdsafe.common.data.graph.transform;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;
import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModule;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModuleList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer.ClusterGraphWriter;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessModuleLoader;
import edu.uci.eecs.crowdsafe.common.datasource.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.execution.ExecutionTraceDirectory;
import edu.uci.eecs.crowdsafe.common.datasource.execution.ExecutionTraceStreamType;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeTraceUtil;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;

public class RawGraphTransformer {

	private static class ClusterNodeIdentity {
		final AutonomousSoftwareDistribution cluster;
		final ClusterNode node;

		ClusterNodeIdentity(AutonomousSoftwareDistribution cluster, ClusterNode node) {
			this.cluster = cluster;
			this.node = node;
		}
	}
	
	private final ArgumentStack args;

	private final ProcessModuleLoader executionModuleLoader = new ProcessModuleLoader();

	// transitory per run:

	private File outputDir = null;
	private ExecutionTraceDataSource dataSource = null;
	private ProcessExecutionModuleSet executionModules = null;
	private ClusterGraphWriter.Directory graphWriters = null;
	private final Map<AutonomousSoftwareDistribution, ClusterModuleList> clusterModules = new HashMap<AutonomousSoftwareDistribution, ClusterModuleList>();

	private final Map<RawExecutionNodeId, ClusterNode> nodesByRawId = new HashMap<RawExecutionNodeId, ClusterNode>();
	private final Map<ClusterNode.Key, ClusterNode> nodesByKey = new HashMap<ClusterNode.Key, ClusterNode>();
	private final Set<Edge<ClusterNode>> edges = new HashSet<Edge<ClusterNode>>();

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
				File runDir = new File(args.pop());
				dataSource = new ExecutionTraceDirectory(runDir, ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES);
				File outputDir = new File(runDir, "cluster");
				outputDir.mkdir();
				graphWriters = new ClusterGraphWriter.Directory(outputDir, dataSource.getProcessName());
				transformGraph();

				outputDir = null;
				dataSource = null;
				executionModules = null;
				graphWriters = null;
				clusterModules.clear();
				nodesByRawId.clear();
				nodesByKey.clear();
				edges.clear();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void transformGraph() throws IOException {
		executionModules = executionModuleLoader.loadModules(dataSource);
		for (AutonomousSoftwareDistribution dist : ConfiguredSoftwareDistributions.getInstance().distributions.values()) {
			clusterModules.put(dist, new ClusterModuleList());
		}

		transformNodes(ExecutionTraceStreamType.GRAPH_NODE);
		transformEdges(ExecutionTraceStreamType.GRAPH_EDGE);
		transformCrossModuleEdges(ExecutionTraceStreamType.CROSS_MODULE_EDGE);

		writeEdges();
		writeNodes();
		writeModules();
		graphWriters.flush();
	}

	private void transformNodes(ExecutionTraceStreamType streamType) throws IOException {
		LittleEndianInputStream input = dataSource.getLittleEndianInputStream(streamType);
		RawGraphEntry.TwoWordFactory factory = new RawGraphEntry.TwoWordFactory(input);

		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.TwoWordEntry nodeEntry = factory.createEntry();
			entryIndex++;

			long absoluteTag = CrowdSafeTraceUtil.getTag(nodeEntry.first);
			int tagVersion = CrowdSafeTraceUtil.getTagVersion(nodeEntry.first);
			MetaNodeType nodeType = CrowdSafeTraceUtil.getNodeMetaType(nodeEntry.first);
			RawExecutionNodeId rawNodeId = new RawExecutionNodeId((int) absoluteTag, tagVersion);

			ModuleInstance moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
			int relativeTag = (int) (absoluteTag - moduleInstance.start);

			AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(moduleInstance.unit);
			ClusterModule clusterModule = clusterModules.get(cluster).establishModule(moduleInstance.unit,
					moduleInstance.version);
			graphWriters.establishClusterWriters(cluster);

			ClusterNode node = new ClusterNode(clusterModule, relativeTag, tagVersion, nodeEntry.second, nodeType);
			nodesByRawId.put(rawNodeId, node);
			nodesByKey.put(node.getKey(), node);
		}
	}

	private void transformEdges(ExecutionTraceStreamType streamType) throws IOException {
		LittleEndianInputStream input = dataSource.getLittleEndianInputStream(streamType);
		RawGraphEntry.TwoWordFactory factory = new RawGraphEntry.TwoWordFactory(input);

		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.TwoWordEntry edgeEntry = factory.createEntry();
			entryIndex++;

			long absoluteFromTag = CrowdSafeTraceUtil.getTag(edgeEntry.first);
			int fromTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.first);
			ClusterNodeIdentity fromNodeId = identifyNode(absoluteFromTag, fromTagVersion, entryIndex, streamType);

			EdgeType type = CrowdSafeTraceUtil.getTagEdgeType(edgeEntry.first);
			int ordinal = CrowdSafeTraceUtil.getEdgeOrdinal(edgeEntry.first);

			long absoluteToTag = CrowdSafeTraceUtil.getTag(edgeEntry.second);
			int toTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.second);
			ClusterNodeIdentity toNodeId = identifyNode(absoluteFromTag, fromTagVersion, entryIndex, streamType);

			if (toNodeId.node == null) {
				if (type != EdgeType.CALL_CONTINUATION)
					Log.log("Error: missing 'to' node %s", toNodeId.node);

				continue;
			}

			if (fromNodeId.cluster == toNodeId.cluster) {
				edges.add(new Edge<ClusterNode>(fromNodeId.node, toNodeId.node, type, ordinal));
			} else {
				throw new IllegalStateException(String.format(
						"Intra-module edge from %s to %s crosses a cluster boundary!", fromNodeId.node, toNodeId.node));
			}
		}
	}

	private void transformCrossModuleEdges(ExecutionTraceStreamType streamType) throws IOException {
		LittleEndianInputStream input = dataSource.getLittleEndianInputStream(streamType);
		RawGraphEntry.ThreeWordFactory factory = new RawGraphEntry.ThreeWordFactory(input);

		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.ThreeWordEntry edgeEntry = factory.createEntry();
			entryIndex++;

			long absoluteFromTag = CrowdSafeTraceUtil.getTag(edgeEntry.first);
			int fromTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.first);
			ClusterNodeIdentity fromNodeId = identifyNode(absoluteFromTag, fromTagVersion, entryIndex, streamType);
			EdgeType type = CrowdSafeTraceUtil.getTagEdgeType(edgeEntry.first);
			int ordinal = CrowdSafeTraceUtil.getEdgeOrdinal(edgeEntry.first);

			long absoluteToTag = CrowdSafeTraceUtil.getTag(edgeEntry.second);
			int toTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.second);
			ClusterNodeIdentity toNodeId = identifyNode(absoluteFromTag, fromTagVersion, entryIndex, streamType);

			if (fromNodeId.cluster == toNodeId.cluster) {
				edges.add(new Edge<ClusterNode>(fromNodeId.node, toNodeId.node, type, ordinal));
			} else {
				ClusterNode entry = new ClusterNode(toNodeId.node.getKey().module, toNodeId.node.getKey().relativeTag,
						toNodeId.node.getKey().instanceId, edgeEntry.third, MetaNodeType.CLUSTER_ENTRY);
				nodesByKey.put(entry.getKey(), entry);
				edges.add(new Edge<ClusterNode>(entry, toNodeId.node, EdgeType.CLUSTER_ENTRY, 0));

				ClusterNode exit = new ClusterNode(fromNodeId.node.getKey().module,
						fromNodeId.node.getKey().relativeTag, fromNodeId.node.getKey().instanceId, edgeEntry.third,
						MetaNodeType.CLUSTER_EXIT);
				nodesByKey.put(exit.getKey(), exit);
				edges.add(new Edge<ClusterNode>(fromNodeId.node, exit, type, ordinal));
			}
		}
	}

	private ClusterNodeIdentity identifyNode(long absoluteTag, int tagVersion, long entryIndex,
			ExecutionTraceStreamType streamType) {
		ModuleInstance moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
		AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
				.get(moduleInstance.unit);
		ClusterModule clusterModule = clusterModules.get(cluster)
				.getModule(moduleInstance.unit, moduleInstance.version);
		ClusterNode.Key key = new ClusterNode.Key(clusterModule, (int) (absoluteTag - moduleInstance.start), tagVersion);
		ClusterNode node = nodesByKey.get(key);
		return new ClusterNodeIdentity(cluster, node);
	}

	private void writeEdges() throws IOException {
		for (Edge<ClusterNode> edge : edges) {
			AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(edge.getFromNode().getKey().module.unit);
			graphWriters.getWriter(cluster).writeEdge(edge);
		}
	}

	private void writeNodes() throws IOException {
		for (ClusterNode node : nodesByKey.values()) {
			AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(node.getKey().module.unit);
			graphWriters.getWriter(cluster).writeNode(node);
		}
	}

	private void writeModules() throws IOException {
		for (Map.Entry<AutonomousSoftwareDistribution, ClusterModuleList> entry : clusterModules.entrySet()) {
			ClusterGraphWriter output = graphWriters.getWriter(entry.getKey());
			if (output != null)
				output.writeModules(entry.getValue());
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
