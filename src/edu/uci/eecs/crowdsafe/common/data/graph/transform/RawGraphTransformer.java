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
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBasicBlock;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModule;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer.ClusterDataWriter;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessModuleLoader;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.io.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.common.io.execution.ExecutionTraceDirectory;
import edu.uci.eecs.crowdsafe.common.io.execution.ExecutionTraceStreamType;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeTraceUtil;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;

public class RawGraphTransformer {

	private static final OptionArgumentMap.BooleanOption verboseOption = OptionArgumentMap.createBooleanOption('v');
	private static final OptionArgumentMap.StringOption logOption = OptionArgumentMap.createStringOption('l');
	private static final OptionArgumentMap.StringOption inputOption = OptionArgumentMap.createStringOption('i');
	private static final OptionArgumentMap.StringOption outputOption = OptionArgumentMap.createStringOption('o');
	private static final OptionArgumentMap.BooleanOption unitClusterOption = OptionArgumentMap.createBooleanOption('u',
			true);

	private final ArgumentStack args;

	private final ProcessModuleLoader executionModuleLoader = new ProcessModuleLoader();

	// transitory per run:

	private File outputDir = null;
	private ExecutionTraceDataSource dataSource = null;
	private ProcessExecutionModuleSet executionModules = null;
	private ClusterDataWriter.Directory<IndexedClusterNode> graphWriters = null;
	private final Map<AutonomousSoftwareDistribution, RawClusterData> dataByCluster = new HashMap<AutonomousSoftwareDistribution, RawClusterData>();
	private final Map<AutonomousSoftwareDistribution, Set<RawEdge>> edgesByCluster = new HashMap<AutonomousSoftwareDistribution, Set<RawEdge>>();

	private final Map<RawTag, IndexedClusterNode> nodesByRawTag = new HashMap<RawTag, IndexedClusterNode>();
	private final Set<Long> flattenedCollisions = new HashSet<Long>();
	private final Map<RawTag, Integer> fakeAnonymousModuleTags = new HashMap<RawTag, Integer>();
	private int fakeAnonymousTagIndex = 0;
	private final Map<AutonomousSoftwareDistribution, AutonomousSoftwareDistribution> blackBoxOwners = new HashMap<AutonomousSoftwareDistribution, AutonomousSoftwareDistribution>();

	public RawGraphTransformer(ArgumentStack args) {
		this.args = args;

		OptionArgumentMap.populateOptions(args, verboseOption, logOption, inputOption, outputOption, unitClusterOption);
	}

	private void run() {
		try {
			if (verboseOption.getValue() || (logOption.getValue() == null)) {
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

			ConfiguredSoftwareDistributions.ClusterMode clusterMode;
			if (unitClusterOption.hasValue())
				clusterMode = ConfiguredSoftwareDistributions.ClusterMode.UNIT;
			else
				clusterMode = ConfiguredSoftwareDistributions.ClusterMode.GROUP;
			ConfiguredSoftwareDistributions.initialize(clusterMode);

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

		final Multimap<Long, RawTag> multiVersionTags = ArrayListMultimap.create();

		boolean foundAppEntryPoint = false;
		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.TwoWordEntry nodeEntry = factory.createEntry();
			entryIndex++;

			long absoluteTag = CrowdSafeTraceUtil.getTag(nodeEntry.first);
			int tagVersion = CrowdSafeTraceUtil.getTagVersion(nodeEntry.first);
			MetaNodeType nodeType = CrowdSafeTraceUtil.getNodeMetaType(nodeEntry.first);

			ModuleInstance moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
			AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(moduleInstance.unit);

			if (nodeType == MetaNodeType.BLACK_BOX_SINGLETON) {
				AutonomousSoftwareDistribution owner = ConfiguredSoftwareDistributions.getInstance()
						.getClusterByAnonymousEntryHash(nodeEntry.second);
				if (owner == null) {
					// error!
				}
				
				// change tag to (((int) nodeEntry.second) | (1 << 0x1f))
				blackBoxOwners.put(cluster, owner);
			}

			int relativeTag;
			ClusterModule clusterModule;
			if (cluster.isAnonymous()) {
				Log.log("Anonymous node with absolute tag 0x%x and hash 0x%x in module instance %s", absoluteTag,
						nodeEntry.second, moduleInstance, cluster);

				clusterModule = establishNodeData(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER).moduleList
						.establishModule(SoftwareModule.ANONYMOUS_MODULE.unit);
				cluster = ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER;
				moduleInstance = ModuleInstance.ANONYMOUS;

				RawTag lookup = new RawTag(absoluteTag, tagVersion);
				Integer tag = fakeAnonymousModuleTags.get(lookup);
				if (tag == null) {
					tag = fakeAnonymousTagIndex++;
					fakeAnonymousModuleTags.put(lookup, tag);
					Log.log("mapping anonymous tag 0x%x-v%d to fake tag 0x%x", absoluteTag, tagVersion, tag);
				}
				relativeTag = tag;
			} else {
				clusterModule = establishNodeData(cluster).moduleList.establishModule(moduleInstance.unit);
				relativeTag = (int) (absoluteTag - moduleInstance.start);
			}

			graphWriters.establishClusterWriters(dataByCluster.get(cluster));

			ClusterNode<?> node;
			if (nodeType == MetaNodeType.CONTEXT_ENTRY) {
				// TODO: unhack this when new data arrives from DR:
				node = new ClusterBoundaryNode(1L /* nodeEntry.second */, MetaNodeType.CLUSTER_ENTRY);
			} else {
				node = new ClusterBasicBlock(clusterModule, relativeTag, cluster.isAnonymous() ? 0 : tagVersion,
						nodeEntry.second, nodeType);
			}
			RawClusterData nodeData = establishNodeData(cluster);
			IndexedClusterNode nodeId = nodeData.addNode(node);

			RawTag rawTag = new RawTag(absoluteTag, tagVersion);
			nodesByRawTag.put(rawTag, nodeId);
			if ((tagVersion > 0) && !cluster.isAnonymous())
				multiVersionTags.put(absoluteTag, rawTag);

			if ((!foundAppEntryPoint) && (nodeType == MetaNodeType.NORMAL)) {
				ClusterBoundaryNode entry = new ClusterBoundaryNode(1L, MetaNodeType.CLUSTER_ENTRY);
				IndexedClusterNode entryId = nodeData.addNode(entry);
				establishEdgeSet(cluster).add(new RawEdge(entryId, nodeId, EdgeType.CLUSTER_ENTRY, 0));

				foundAppEntryPoint = true;
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
					continue;
				}
			}

			if (fromNodeId.cluster == toNodeId.cluster) {
				RawEdge edge = new RawEdge(fromNodeId, toNodeId, type, ordinal);
				if (fromNodeId.cluster.isAnonymous())
					establishEdgeSet(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER).add(edge);
				else
					establishEdgeSet(fromNodeId.cluster).add(edge);
			} else {
				Log.log("Error! Intra-module edge from %s to %s crosses a cluster boundary (%s to %s)!",
						fromNodeId.node, toNodeId.node, fromNodeId.cluster, toNodeId.cluster);
				// throw new IllegalStateException(String.format(
				// "Intra-module edge from %s to %s crosses a cluster boundary!", fromNodeId.node, toNodeId.node));
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
			EdgeType type = CrowdSafeTraceUtil.getTagEdgeType(edgeEntry.first);
			int ordinal = CrowdSafeTraceUtil.getEdgeOrdinal(edgeEntry.first);
			IndexedClusterNode fromNodeId = identifyNode(absoluteFromTag, fromTagVersion, entryIndex, streamType);

			long absoluteToTag = CrowdSafeTraceUtil.getTag(edgeEntry.second);
			int toTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.second);
			IndexedClusterNode toNodeId = identifyNode(absoluteToTag, toTagVersion, entryIndex, streamType);

			long hash = edgeEntry.third;

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
			} else if (fromNodeId.cluster.isAnonymous() && toNodeId.cluster.isAnonymous()) {
				establishEdgeSet(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER).add(
						new RawEdge(fromNodeId, toNodeId, type, ordinal));
			} else {
				ClusterBoundaryNode entry = new ClusterBoundaryNode(hash, MetaNodeType.CLUSTER_ENTRY);
				IndexedClusterNode entryId = dataByCluster.get(toNodeId.cluster).addNode(entry);
				establishEdgeSet(toNodeId.cluster).add(new RawEdge(entryId, toNodeId, EdgeType.CLUSTER_ENTRY, 0));

				ClusterBoundaryNode exit = new ClusterBoundaryNode(hash, MetaNodeType.CLUSTER_EXIT);
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

		AutonomousSoftwareDistribution cluster;
		if (moduleInstance.unit.isAnonymous)
			cluster = ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER;
		else
			cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit.get(moduleInstance.unit);
		ClusterModule clusterModule = dataByCluster.get(cluster).moduleList.getModule(moduleInstance.unit);

		if ((moduleInstance.unit == SoftwareUnit.DYNAMORIO) || moduleInstance.unit.isAnonymous) {
			IndexedClusterNode node = nodesByRawTag.get(new RawTag(absoluteTag, tagVersion));
			return node;
		}

		long tag = (absoluteTag - moduleInstance.start);
		if ((tagVersion > 0) && (flattenedCollisions.contains(absoluteTag)))
			tagVersion = 0;

		ClusterBasicBlock.Key key = new ClusterBasicBlock.Key(clusterModule, tag, tagVersion);
		IndexedClusterNode node = dataByCluster.get(cluster).getNode(key);
		return node;
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
