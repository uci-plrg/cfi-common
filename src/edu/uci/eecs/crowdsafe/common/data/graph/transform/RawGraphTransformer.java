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
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBasicBlock;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModule;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer.ClusterDataWriter;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.loader.ProcessModuleLoader;
import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
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
	private final Map<AutonomousSoftwareDistribution, RawClusterData> nodesByCluster = new HashMap<AutonomousSoftwareDistribution, RawClusterData>();
	private final Map<AutonomousSoftwareDistribution, Set<RawEdge>> edgesByCluster = new HashMap<AutonomousSoftwareDistribution, Set<RawEdge>>();

	private final Map<RawTag, IndexedClusterNode> nodesByRawTag = new HashMap<RawTag, IndexedClusterNode>();
	private final Map<RawTag, Integer> fakeAnonymousModuleTags = new HashMap<RawTag, Integer>();
	private int fakeAnonymousTagIndex = ClusterNode.FAKE_ANONYMOUS_TAG_START;
	private final Map<AutonomousSoftwareDistribution, AutonomousSoftwareDistribution> blackBoxOwners = new HashMap<AutonomousSoftwareDistribution, AutonomousSoftwareDistribution>();
	private final Map<AutonomousSoftwareDistribution, ClusterNode<?>> blackBoxSingletons = new HashMap<AutonomousSoftwareDistribution, ClusterNode<?>>();

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
				Log.log("Transform %s to %s", runDir.getAbsolutePath(), outputDir.getAbsolutePath());
				transformGraph();

				outputDir = null;
				dataSource = null;
				executionModules = null;
				graphWriters = null;
				nodesByCluster.clear();
				edgesByCluster.clear();
				nodesByRawTag.clear();
				fakeAnonymousModuleTags.clear();
				fakeAnonymousTagIndex = ClusterNode.FAKE_ANONYMOUS_TAG_START;
				blackBoxOwners.clear();
				blackBoxSingletons.clear();
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

		fakeAnonymousModuleTags.put(new RawTag(ClusterNode.PROCESS_ENTRY_SINGLETON, 0),
				ClusterNode.PROCESS_ENTRY_SINGLETON);
		fakeAnonymousModuleTags.put(new RawTag(ClusterNode.SYSCALL_SINGLETON, 0), ClusterNode.SYSCALL_SINGLETON);
		fakeAnonymousModuleTags.put(new RawTag(ClusterNode.DYNAMORIO_INTERCEPTION_RETURN_SINGLETON, 0),
				ClusterNode.DYNAMORIO_INTERCEPTION_RETURN_SINGLETON);

		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.TwoWordEntry nodeEntry = factory.createEntry();
			entryIndex++;

			long absoluteTag = CrowdSafeTraceUtil.getTag(nodeEntry.first);
			int tagVersion = CrowdSafeTraceUtil.getTagVersion(nodeEntry.first);
			MetaNodeType nodeType = CrowdSafeTraceUtil.getNodeMetaType(nodeEntry.first);

			ModuleInstance moduleInstance;
			if (nodeType == MetaNodeType.SINGLETON) {
				if ((absoluteTag == ClusterNode.PROCESS_ENTRY_SINGLETON)
						|| (absoluteTag == ClusterNode.SYSCALL_SINGLETON)) {
					moduleInstance = ModuleInstance.SYSTEM;
				} else if (absoluteTag == ClusterNode.DYNAMORIO_INTERCEPTION_RETURN_SINGLETON) {
					moduleInstance = ModuleInstance.DYNAMORIO;
				} else if ((absoluteTag >= ClusterNode.BLACK_BOX_SINGLETON_START)
						&& (absoluteTag < ClusterNode.BLACK_BOX_SINGLETON_END)) {
					moduleInstance = ModuleInstance.ANONYMOUS;
				} else {
					throw new InvalidGraphException("Error: unknown singleton with tag 0x%x!", absoluteTag);
				}
			} else {
				moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
			}
			AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(moduleInstance.unit);

			if (cluster == null)
				toString();

			int relativeTag;
			ClusterModule clusterModule;
			AutonomousSoftwareDistribution blackBoxOwner = null;
			boolean isNewBlackBoxSingleton = false;
			if (cluster.isAnonymous()) {
				if ((absoluteTag == ClusterNode.PROCESS_ENTRY_SINGLETON)
						|| (absoluteTag == ClusterNode.SYSCALL_SINGLETON)) {
					clusterModule = establishNodeData(ConfiguredSoftwareDistributions.SYSTEM_CLUSTER).moduleList
							.establishModule(SoftwareModule.SYSTEM_MODULE.unit);
					cluster = ConfiguredSoftwareDistributions.SYSTEM_CLUSTER;
					moduleInstance = ModuleInstance.SYSTEM;
				} else if (absoluteTag == ClusterNode.DYNAMORIO_INTERCEPTION_RETURN_SINGLETON) {
					clusterModule = establishNodeData(ConfiguredSoftwareDistributions.DYNAMORIO_CLUSTER).moduleList
							.establishModule(SoftwareModule.DYNAMORIO_MODULE.unit);
					cluster = ConfiguredSoftwareDistributions.DYNAMORIO_CLUSTER;
					moduleInstance = ModuleInstance.DYNAMORIO;
				} else {
					clusterModule = establishNodeData(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER).moduleList
							.establishModule(SoftwareModule.ANONYMOUS_MODULE.unit);
					cluster = ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER;
					moduleInstance = ModuleInstance.ANONYMOUS;
					if (nodeType == MetaNodeType.SINGLETON) {
						blackBoxOwner = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousEntryHash(
								nodeEntry.second);
						if (blackBoxOwner == null)
							new InvalidGraphException("Error: cannot find the owner of black box with entry 0x%x!",
									nodeEntry.second);

						blackBoxOwners.put(cluster, blackBoxOwner);
						// TODO: removing extraneous nodes requires patching the data set for the whole
						// anonymous module
					}
				}

				RawTag lookup = new RawTag(absoluteTag, tagVersion);
				Integer tag = null;
				if (blackBoxOwner == null) {
					tag = fakeAnonymousModuleTags.get(lookup);
				} else {
					ClusterNode<?> singleton = blackBoxSingletons.get(blackBoxOwner);
					if (singleton != null) {
						tag = singleton.getRelativeTag();
						fakeAnonymousModuleTags.put(lookup, tag);
					}
				}

				if (tag == null) {
					tag = fakeAnonymousTagIndex++;
					fakeAnonymousModuleTags.put(lookup, tag);
					Log.log("Mapping 0x%x-v%d => 0x%x for module %s (hash 0x%x)", absoluteTag, tagVersion, tag,
							moduleInstance.unit.filename, nodeEntry.second);

					isNewBlackBoxSingleton = (blackBoxOwner != null);
				}
				relativeTag = tag;
			} else {
				relativeTag = (int) (absoluteTag - moduleInstance.start);
			}

			ClusterNode<?> node;
			IndexedClusterNode nodeId;
			RawClusterData nodeData = establishNodeData(cluster);
			if ((blackBoxOwner == null) || isNewBlackBoxSingleton) {
				clusterModule = establishNodeData(cluster).moduleList.establishModule(moduleInstance.unit);
				node = new ClusterBasicBlock(clusterModule, relativeTag, cluster.isAnonymous() ? 0 : tagVersion,
						nodeEntry.second, nodeType);
				if (isNewBlackBoxSingleton)
					blackBoxSingletons.put(blackBoxOwner, node);
				nodeId = nodeData.addNode(node);
			} else {
				node = blackBoxSingletons.get(blackBoxOwner);
				nodeId = nodeData.getNode(node.getKey());
			}

			graphWriters.establishClusterWriters(nodesByCluster.get(cluster));

			if (cluster.isAnonymous())
				nodesByRawTag.put(new RawTag(absoluteTag, tagVersion), nodeId);
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
				if (toNodeId == null) {
					Log.log("Error: both nodes missing in edge 0x%x-v%d -> 0x%x-v%d (%s)", absoluteFromTag,
							fromTagVersion, absoluteToTag, toTagVersion, type);
				} else {
					Log.log("Error: missing 'from' node 0x%x-v%d in edge to %s 0x%x-v%d (%s)", absoluteFromTag,
							fromTagVersion, toNodeId.cluster.name, absoluteToTag, toTagVersion, type);
				}
				continue;
			}

			if (toNodeId == null) {
				Log.log("Error: missing 'to' node 0x%x-v%d", absoluteToTag, toTagVersion);
				continue;
			}

			/**
			 * <pre>
			if (fromNodeId.cluster == ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER) {
				if ((blackBoxSingletons.containsValue(fromNodeId.node))
						|| (blackBoxSingletons.containsValue(toNodeId.node))) {
					toString();
				}
			}
			 */

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
				if (toNodeId == null) {
					Log.log("Error: both nodes missing in edge 0x%x-v%d -> 0x%x-v%d", absoluteFromTag, fromTagVersion,
							absoluteToTag, toTagVersion);
				} else {
					Log.log("Error: missing 'from' node 0x%x-v%d in edge to %s 0x%x-v%d ", absoluteFromTag,
							fromTagVersion, toNodeId.cluster.name, absoluteToTag, toTagVersion);
				}
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
				IndexedClusterNode entryId = nodesByCluster.get(toNodeId.cluster).addNode(entry);
				establishEdgeSet(toNodeId.cluster).add(new RawEdge(entryId, toNodeId, EdgeType.CLUSTER_ENTRY, 0));

				ClusterBoundaryNode exit = new ClusterBoundaryNode(hash, MetaNodeType.CLUSTER_EXIT);
				IndexedClusterNode exitId = nodesByCluster.get(fromNodeId.cluster).addNode(exit);
				establishEdgeSet(fromNodeId.cluster).add(new RawEdge(fromNodeId, exitId, type, ordinal));
			}
		}
	}

	private RawClusterData establishNodeData(AutonomousSoftwareDistribution cluster) {
		RawClusterData data = nodesByCluster.get(cluster);
		if (data == null) {
			data = new RawClusterData(cluster);
			nodesByCluster.put(cluster, data);
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

		ClusterModule clusterModule = null;
		RawClusterData nodeData = nodesByCluster.get(cluster);
		if (nodeData == null) {
			toString();
		} else {
			clusterModule = nodeData.moduleList.getModule(moduleInstance.unit);
		}

		// ClusterModule clusterModule = nodesByCluster.get(cluster).moduleList.getModule(moduleInstance.unit);

		if ((moduleInstance.unit == SoftwareModule.DYNAMORIO_MODULE.unit) || moduleInstance.unit.isAnonymous) {
			IndexedClusterNode node = nodesByRawTag.get(new RawTag(absoluteTag, tagVersion));
			return node;
		}

		long tag = (absoluteTag - moduleInstance.start);
		ClusterBasicBlock.Key key = new ClusterBasicBlock.Key(clusterModule, tag, tagVersion);
		IndexedClusterNode node = nodesByCluster.get(cluster).getNode(key);
		return node;
	}

	private void writeNodes() throws IOException {
		for (AutonomousSoftwareDistribution cluster : nodesByCluster.keySet()) {
			ClusterDataWriter<IndexedClusterNode> writer = graphWriters.getWriter(cluster);
			for (IndexedClusterNode node : nodesByCluster.get(cluster).getSortedNodeList()) {
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
		for (AutonomousSoftwareDistribution cluster : nodesByCluster.keySet()) {
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
