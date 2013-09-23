package edu.uci.eecs.crowdsafe.common.data.graph.transform;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;
import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBasicBlock;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModule;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer.ClusterDataWriter;
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

	private static final OptionArgumentMap.BooleanOption verboseOption = OptionArgumentMap.createBooleanOption('v');
	private static final OptionArgumentMap.StringOption logOption = OptionArgumentMap.createStringOption('l');
	private static final OptionArgumentMap.StringOption inputOption = OptionArgumentMap.createStringOption('i');
	private static final OptionArgumentMap.StringOption outputOption = OptionArgumentMap.createStringOption('o');

	private final ArgumentStack args;

	private final ProcessModuleLoader executionModuleLoader = new ProcessModuleLoader();

	// transitory per run:

	private File outputDir = null;
	private ExecutionTraceDataSource dataSource = null;
	private ProcessExecutionModuleSet executionModules = null;
	private ClusterDataWriter.Directory<IndexedClusterNode> graphWriters = null;
	private final Map<AutonomousSoftwareDistribution, RawClusterData> dataByCluster = new HashMap<AutonomousSoftwareDistribution, RawClusterData>();
	private final Map<AutonomousSoftwareDistribution, Set<RawEdge>> edgesByCluster = new HashMap<AutonomousSoftwareDistribution, Set<RawEdge>>();

	private final Set<Long> flattenedCollisions = new HashSet<Long>();

	// for resolving tag versions on call continuation targets
	// private final Map<RawTag, Integer> latestObservedVersion = new HashMap<RawTag, Integer>();
	// private final Map<RawTag, Integer> maximumVersion = new HashMap<RawTag, Integer>();
	// private final Map<RawTag, RawEdge> pendingCallContinuations = new HashMap<RawTag, RawEdge>();

	public RawGraphTransformer(ArgumentStack args) {
		this.args = args;

		OptionArgumentMap.populateOptions(args, verboseOption, logOption, inputOption, outputOption);
	}

	private void run() {
		try {
			if (verboseOption.getValue()) {
				Log.addOutput(System.out);
			}
			if (logOption.getValue() != null) {
				Log.addOutput(new File(logOption.getValue()));
			}

			if ((inputOption.getValue() == null) != (outputOption.getValue() == null))
				throw new IllegalArgumentException("The input (-i) and output (-o) options must be used together!");

			List<String> pathList = new ArrayList<String>();
			if (inputOption.getValue() == null) {
				while (args.size() > 0)
					pathList.add(args.pop());
			} else {
				if (args.size() > 0)
					throw new IllegalArgumentException(
							"The input (-i) and output (-o) options cannot be used with a list of run directories!");

				pathList.add(inputOption.getValue());
			}

			CrowdSafeConfiguration.initialize(EnumSet.of(CrowdSafeConfiguration.Environment.CROWD_SAFE_COMMON_DIR));
			ConfiguredSoftwareDistributions.initialize();

			for (String inputPath : pathList) {
				File runDir = new File(inputPath);
				if (!(runDir.exists() && runDir.isDirectory())) {
					Log.log("Warning: input path %s is not a directory!", runDir.getAbsolutePath());
					continue;
				}

				dataSource = new ExecutionTraceDirectory(runDir, ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES);

				File outputDir;
				if (outputOption.getValue() == null) {
					outputDir = new File(runDir, "cluster");
				} else {
					outputDir = new File(outputOption.getValue());
				}
				outputDir.mkdirs();
				graphWriters = new ClusterDataWriter.Directory<IndexedClusterNode>(outputDir,
						dataSource.getProcessName());
				transformGraph();

				outputDir = null;
				dataSource = null;
				executionModules = null;
				graphWriters = null;
				dataByCluster.clear();
				edgesByCluster.clear();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void transformGraph() throws IOException {
		executionModules = executionModuleLoader.loadModules(dataSource);

		transformNodes(ExecutionTraceStreamType.GRAPH_NODE);
		transformEdges(ExecutionTraceStreamType.GRAPH_EDGE);
		transformCrossModuleEdges(ExecutionTraceStreamType.CROSS_MODULE_EDGE);

		writeNodes();
		writeEdges();
		writeModules();
		graphWriters.flush();
	}

	private void transformNodes(ExecutionTraceStreamType streamType) throws IOException {
		LittleEndianInputStream input = dataSource.getLittleEndianInputStream(streamType);
		RawGraphEntry.TwoWordFactory factory = new RawGraphEntry.TwoWordFactory(input);

		final Map<RawTag, IndexedClusterNode> nodesByRawTag = new HashMap<RawTag, IndexedClusterNode>();
		final Multimap<Long, RawTag> multiVersionTags = ArrayListMultimap.create();

		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.TwoWordEntry nodeEntry = factory.createEntry();
			entryIndex++;

			long absoluteTag = CrowdSafeTraceUtil.getTag(nodeEntry.first);
			int tagVersion = CrowdSafeTraceUtil.getTagVersion(nodeEntry.first);
			MetaNodeType nodeType = CrowdSafeTraceUtil.getNodeMetaType(nodeEntry.first);

			ModuleInstance moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
			int relativeTag = (int) (absoluteTag - moduleInstance.start);

			AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(moduleInstance.unit);
			ClusterModule clusterModule = establishNodeData(cluster).moduleList.establishModule(moduleInstance.unit,
					moduleInstance.version);
			graphWriters.establishClusterWriters(cluster, dataByCluster.get(cluster));

			ClusterBasicBlock node = new ClusterBasicBlock(clusterModule, relativeTag, tagVersion, nodeEntry.second,
					nodeType);
			RawClusterData nodeData = establishNodeData(cluster);
			IndexedClusterNode nodeId = nodeData.addNode(node);

			if (absoluteTag == 0x7228c507L)
				toString();

			if (!clusterModule.unit.equals(SoftwareDistributionUnit.UNKNOWN)) {
				RawTag rawTag = new RawTag(absoluteTag, tagVersion);
				nodesByRawTag.put(rawTag, nodeId);
				if (tagVersion > 0)
					multiVersionTags.put(absoluteTag, rawTag);
			}
			// if (tagVersion > 0)
			// maximumVersion.put(new RawTag(nodeId), node.getInstanceId());

			if ((dataByCluster.size() == 1) && (nodeData.size() == 1)) {
				ClusterBoundaryNode entry = new ClusterBoundaryNode(1L, MetaNodeType.CLUSTER_ENTRY);
				IndexedClusterNode entryId = nodeData.addNode(entry);
				establishEdgeSet(cluster).add(new RawEdge(entryId, nodeId, EdgeType.CLUSTER_ENTRY, 0));
			}
		}

		Set<ClusterModule> modules = new HashSet<ClusterModule>();
		for (Long absoluteTag : multiVersionTags.keySet()) {
			Collection<RawTag> versions = multiVersionTags.get(absoluteTag);
			versions.add(new RawTag(absoluteTag, 0));

			boolean hasCollision = false;
			modules.clear();
			for (RawTag version : versions) {
				IndexedClusterNode node = nodesByRawTag.get(version);
				if (modules.contains(node.node.getModule())) {
					hasCollision = true;
					break;
				}
				modules.add(node.node.getModule());
			}

			if (absoluteTag == 0x7228c507L)
				toString();

			if (hasCollision)
				continue;

			flattenedCollisions.add(absoluteTag);

			for (RawTag version : versions) {
				IndexedClusterNode node = nodesByRawTag.get(version);
				RawClusterData nodeData = establishNodeData(node.cluster);
				nodeData.replace(node, node.resetToVersionZero());
			}
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
			IndexedClusterNode fromNodeId = identifyNode(absoluteFromTag, fromTagVersion, entryIndex, streamType);

			EdgeType type = CrowdSafeTraceUtil.getTagEdgeType(edgeEntry.first);
			int ordinal = CrowdSafeTraceUtil.getEdgeOrdinal(edgeEntry.first);

			long absoluteToTag = CrowdSafeTraceUtil.getTag(edgeEntry.second);
			int toTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.second);

			if (absoluteToTag == 0x7228c507L)
				toString();

			IndexedClusterNode toNodeId = identifyNode(absoluteToTag, toTagVersion, entryIndex, streamType);

			if (fromNodeId == null) {
				Log.log("Error: missing 'from' node 0x%x-v%d", absoluteFromTag, fromTagVersion);
				continue;
			}

			if (toNodeId == null) {
				if (type == EdgeType.CALL_CONTINUATION) {
					if (toTagVersion > 0)
						toNodeId = identifyNode(absoluteToTag, 0, entryIndex, streamType);
					if (toNodeId == null)
						continue;
				} else {
					Log.log("Error: missing 'to' node 0x%x-v%d", absoluteToTag, toTagVersion);
				}
			}

			/**
			 * <pre>
			if (type == EdgeType.CALL_CONTINUATION) {
				// if a later version of the `toNode` has been seen already, use that version
				RawTag toTag = new RawTag(toNodeId);
				Integer latestVersion = latestObservedVersion.get(toTag);
				if ((latestVersion != null) && (toTagVersion < latestVersion))
					toNodeId = identifyNode(absoluteToTag, latestVersion, entryIndex, streamType);
			}
			 */

			if (fromNodeId.cluster == toNodeId.cluster) {
				RawEdge edge = new RawEdge(fromNodeId, toNodeId, type, ordinal);
				establishEdgeSet(fromNodeId.cluster).add(edge);

				/**
				 * <pre>
				if (type == EdgeType.CALL_CONTINUATION) {
					// if later versions exist for `edge.toNode`, pend it as having an ambiguous `toNode.version`
					RawTag toTag = new RawTag(toNodeId);
					Integer maxVersion = maximumVersion.get(toTag);

					if (absoluteToTag == 0x7228b6faL)
						toString();

					if ((maxVersion != null) && (toTagVersion < maxVersion))
						pendingCallContinuations.put(toTag, edge);
				}

				// if `edge` follows a CC having an ambiguous `toNode` version, resolve it as `edge.fromNode`
				RawEdge continuation = pendingCallContinuations.remove(new RawTag(fromNodeId));
				if (continuation != null)
					if (continuation.getToNode().node.getInstanceId() == (fromNodeId.node.getInstanceId() - 1))
						continuation.setToNode(fromNodeId);
				 */
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
			IndexedClusterNode fromNodeId = identifyNode(absoluteFromTag, fromTagVersion, entryIndex, streamType);
			EdgeType type = CrowdSafeTraceUtil.getTagEdgeType(edgeEntry.first);
			int ordinal = CrowdSafeTraceUtil.getEdgeOrdinal(edgeEntry.first);

			long absoluteToTag = CrowdSafeTraceUtil.getTag(edgeEntry.second);
			int toTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.second);
			IndexedClusterNode toNodeId = identifyNode(absoluteToTag, toTagVersion, entryIndex, streamType);

			if (fromNodeId == null) {
				Log.log("Error: missing 'from' node 0x%x-v%d", absoluteFromTag, fromTagVersion);
				continue;
			}

			if (toNodeId == null) {
				Log.log("Error: missing 'to' node 0x%x-v%d", absoluteToTag, toTagVersion);
				continue;
			}

			if (fromNodeId.cluster == toNodeId.cluster) {
				establishEdgeSet(fromNodeId.cluster).add(new RawEdge(fromNodeId, toNodeId, type, ordinal));
			} else {
				ClusterBoundaryNode entry = new ClusterBoundaryNode(edgeEntry.third, MetaNodeType.CLUSTER_ENTRY);
				IndexedClusterNode entryId = dataByCluster.get(toNodeId.cluster).addNode(entry);
				establishEdgeSet(toNodeId.cluster).add(new RawEdge(entryId, toNodeId, EdgeType.CLUSTER_ENTRY, 0));

				ClusterBoundaryNode exit = new ClusterBoundaryNode(edgeEntry.third, MetaNodeType.CLUSTER_EXIT);
				IndexedClusterNode exitId = dataByCluster.get(fromNodeId.cluster).addNode(exit);
				establishEdgeSet(fromNodeId.cluster).add(new RawEdge(fromNodeId, exitId, type, ordinal));
			}
		}
	}

	private RawClusterData establishNodeData(AutonomousSoftwareDistribution cluster) {
		RawClusterData data = dataByCluster.get(cluster);
		if (data == null) {
			data = new RawClusterData(cluster);
			dataByCluster.put(cluster, data);
		}
		return data;
	}

	private Set<RawEdge> establishEdgeSet(AutonomousSoftwareDistribution cluster) {
		Set<RawEdge> set = edgesByCluster.get(cluster);
		if (set == null) {
			set = new HashSet<RawEdge>();
			edgesByCluster.put(cluster, set);
		}
		return set;
	}

	private IndexedClusterNode identifyNode(long absoluteTag, int tagVersion, long entryIndex,
			ExecutionTraceStreamType streamType) {
		ModuleInstance moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
		AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
				.get(moduleInstance.unit);

		ClusterModule clusterModule = dataByCluster.get(cluster).moduleList.getModule(moduleInstance.unit,
				moduleInstance.version);
		if ((tagVersion > 0) && (flattenedCollisions.contains(absoluteTag)))
			tagVersion = 0;
		ClusterBasicBlock.Key key = new ClusterBasicBlock.Key(clusterModule,
				(int) (absoluteTag - moduleInstance.start), tagVersion);
		return dataByCluster.get(cluster).getNode(key);
	}

	private void writeNodes() throws IOException {
		for (AutonomousSoftwareDistribution cluster : dataByCluster.keySet()) {
			ClusterDataWriter<IndexedClusterNode> writer = graphWriters.getWriter(cluster);
			for (IndexedClusterNode node : dataByCluster.get(cluster).getSortedNodeList()) {
				writer.writeNode(node);
			}
		}
	}

	private void writeEdges() throws IOException {
		for (Map.Entry<AutonomousSoftwareDistribution, Set<RawEdge>> clusterEdgeList : edgesByCluster.entrySet()) {
			ClusterDataWriter<IndexedClusterNode> writer = graphWriters.getWriter(clusterEdgeList.getKey());
			for (RawEdge edge : clusterEdgeList.getValue()) {
				writer.writeEdge(edge);
			}
		}
	}

	private void writeModules() throws IOException {
		for (AutonomousSoftwareDistribution cluster : dataByCluster.keySet()) {
			ClusterDataWriter<IndexedClusterNode> output = graphWriters.getWriter(cluster);
			if (output != null)
				output.writeModules();
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
