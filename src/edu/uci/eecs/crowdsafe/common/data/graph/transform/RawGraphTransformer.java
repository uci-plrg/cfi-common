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
	private final Map<Long, IndexedClusterNode> syscallSingletons = new HashMap<Long, IndexedClusterNode>();
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
				try {
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
				} catch (Throwable t) {
					Log.log("Error transforming %s", inputPath);
					Log.log(t);

					System.err.println("Error transforming " + inputPath);
					t.printStackTrace();
				}

				outputDir = null;
				dataSource = null;
				executionModules = null;
				graphWriters = null;
				nodesByCluster.clear();
				edgesByCluster.clear();
				nodesByRawTag.clear();
				fakeAnonymousModuleTags.clear();
				fakeAnonymousTagIndex = ClusterNode.FAKE_ANONYMOUS_TAG_START;
				syscallSingletons.clear();
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

		RawClusterData nodeData = establishNodeData(ConfiguredSoftwareDistributions.SYSTEM_CLUSTER);
		nodeData.moduleList.establishModule(ModuleInstance.SYSTEM_MODULE.unit);
		ClusterNode<?> node = new ClusterBasicBlock(SoftwareModule.SYSTEM_MODULE, ClusterNode.PROCESS_ENTRY_SINGLETON,
				0, ClusterNode.PROCESS_ENTRY_SINGLETON, MetaNodeType.SINGLETON);
		IndexedClusterNode nodeId = nodeData.addNode(node);
		RawTag rawTag = new RawTag(ClusterNode.PROCESS_ENTRY_SINGLETON, 0);
		fakeAnonymousModuleTags.put(rawTag, ClusterNode.PROCESS_ENTRY_SINGLETON);
		nodesByRawTag.put(rawTag, nodeId);
		graphWriters.establishClusterWriters(nodesByCluster.get(ConfiguredSoftwareDistributions.SYSTEM_CLUSTER));

		node = new ClusterBasicBlock(SoftwareModule.SYSTEM_MODULE, ClusterNode.SYSTEM_SINGLETON, 0,
				ClusterNode.SYSTEM_SINGLETON, MetaNodeType.SINGLETON);
		nodeId = nodeData.addNode(node);
		rawTag = new RawTag(ClusterNode.SYSTEM_SINGLETON, 0);
		fakeAnonymousModuleTags.put(rawTag, ClusterNode.SYSTEM_SINGLETON);
		nodesByRawTag.put(rawTag, nodeId);

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
						|| (absoluteTag == ClusterNode.SYSTEM_SINGLETON)) {
					moduleInstance = ModuleInstance.SYSTEM;
				} else if ((absoluteTag >= ClusterNode.BLACK_BOX_SINGLETON_START)
						&& (absoluteTag < ClusterNode.BLACK_BOX_SINGLETON_END)) {
					moduleInstance = ModuleInstance.ANONYMOUS;
				} else {
					throw new InvalidGraphException("Error: unknown singleton with tag 0x%x!", absoluteTag);
				}
			} else if ((absoluteTag >= ClusterNode.BLACK_BOX_SINGLETON_START) // FIXME: temporary hack
					&& (absoluteTag < ClusterNode.BLACK_BOX_SINGLETON_END)) {
				moduleInstance = ModuleInstance.ANONYMOUS;
			} else {
				moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
			}
			if (moduleInstance == null) {
				Log.log("Error: cannot find the module for node 0x%x-v%d (type %s)", absoluteTag, tagVersion, nodeType);
				continue;
			}

			AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(moduleInstance.unit);

			int relativeTag;
			ClusterModule clusterModule;
			AutonomousSoftwareDistribution blackBoxOwner = null;
			boolean isNewBlackBoxSingleton = false;
			if (cluster.isAnonymous()) {
				if ((absoluteTag == ClusterNode.PROCESS_ENTRY_SINGLETON)
						|| (absoluteTag == ClusterNode.SYSTEM_SINGLETON)) {
					clusterModule = establishNodeData(ConfiguredSoftwareDistributions.SYSTEM_CLUSTER).moduleList
							.establishModule(SoftwareModule.SYSTEM_MODULE.unit);
					cluster = ConfiguredSoftwareDistributions.SYSTEM_CLUSTER;
					moduleInstance = ModuleInstance.SYSTEM;
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

						// TODO: removing extraneous nodes requires patching the data set for the whole
						// anonymous module
					}
				}

				RawTag lookup = new RawTag(absoluteTag, tagVersion);
				Integer tag = null;
				if (blackBoxOwner == null) {
					tag = fakeAnonymousModuleTags.get(lookup);
				} else { // this is not necessary now, there is only one instance of the singleton
					ClusterNode<?> singleton = blackBoxSingletons.get(blackBoxOwner);
					if (singleton != null) {
						tag = singleton.getRelativeTag();
						fakeAnonymousModuleTags.put(lookup, tag);
					}
				}

				if (tag == null) {
					if (blackBoxOwner != null) {
						isNewBlackBoxSingleton = true;
						tag = (int) absoluteTag;
					} else {
						tag = fakeAnonymousTagIndex++;
						fakeAnonymousModuleTags.put(lookup, tag);
						Log.log("Mapping 0x%x-v%d => 0x%x for module %s (hash 0x%x)", absoluteTag, tagVersion, tag,
								moduleInstance.unit.filename, nodeEntry.second);
					}
				}
				relativeTag = tag;
			} else {
				relativeTag = (int) (absoluteTag - moduleInstance.start);
			}

			nodeData = establishNodeData(cluster);
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
					Log.log("Error: both nodes missing in edge (0x%x-v%d) -%s-%d-> (0x%x-v%d)", absoluteFromTag,
							fromTagVersion, type.code, ordinal, absoluteToTag, toTagVersion);
				} else {
					Log.log("Error in cluster %s: missing 'from' node: (0x%x-v%d) -%s-%d-> (0x%x-v%d)",
							toNodeId.cluster.getUnitFilename(), absoluteFromTag, fromTagVersion, type.code, ordinal,
							absoluteToTag, toTagVersion);
				}
				continue;
			}

			if (toNodeId == null) {
				Log.log("Error in cluster %s: missing 'to' node: (0x%x-v%d) -%s-%d-> (0x%x-v%d)",
						fromNodeId.cluster.getUnitFilename(), absoluteFromTag, fromTagVersion, type.code, ordinal,
						absoluteToTag, toTagVersion);
				continue;
			}

			if (type.isHighOrdinal(ordinal))
				Log.log("Warning: high ordinal in %s edge (0x%x-v%d) -%s-%d-> (0x%x-v%d)",
						fromNodeId.cluster.getUnitFilename(), absoluteFromTag, fromTagVersion, type.code, ordinal,
						absoluteToTag, toTagVersion);

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
					Log.log("Error: both nodes missing in cross-module edge 0x%x-v%d -> 0x%x-v%d", absoluteFromTag,
							fromTagVersion, absoluteToTag, toTagVersion);
				} else {
					Log.log("Error: missing 'from' node 0x%x-v%d in cross-module edge to %s(0x%x-v%d) ",
							absoluteFromTag, fromTagVersion, toNodeId.cluster.getUnitFilename(), absoluteToTag,
							toTagVersion);
				}
				continue;
			}

			if (toNodeId == null) {
				Log.log("Error: missing 'to' node 0x%x-v%d in cross-module edge from %s 0x%x-v%d", absoluteToTag,
						toTagVersion, fromNodeId.cluster.getUnitFilename(), absoluteFromTag, fromTagVersion);
				continue;
			}

			if (type.isHighOrdinal(ordinal))
				Log.log("Warning: high ordinal in cross-module edge %s(0x%x-v%d) -%s-%d-> %s(0x%x-v%d)",
						fromNodeId.cluster.getUnitFilename(), absoluteFromTag, fromTagVersion, type.code, ordinal,
						toNodeId.cluster.getUnitFilename(), absoluteToTag, toTagVersion);

			if (fromNodeId.cluster == toNodeId.cluster) {
				establishEdgeSet(fromNodeId.cluster).add(new RawEdge(fromNodeId, toNodeId, type, ordinal));
			} else if (fromNodeId.cluster.isAnonymous() && toNodeId.cluster.isAnonymous()) {
				establishEdgeSet(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER).add(
						new RawEdge(fromNodeId, toNodeId, type, ordinal));
			} else {
				ClusterBoundaryNode entry = new ClusterBoundaryNode(hash, MetaNodeType.CLUSTER_ENTRY);
				IndexedClusterNode entryId = nodesByCluster.get(toNodeId.cluster).addNode(entry);
				establishEdgeSet(toNodeId.cluster).add(new RawEdge(entryId, toNodeId, EdgeType.CLUSTER_ENTRY, 0));

				// if ((fromNodeId.node.getRelativeTag() != ClusterNode.PROCESS_ENTRY_SINGLETON)
				// && (fromNodeId.node.getRelativeTag() != ClusterNode.SYSTEM_SINGLETON)) {
				ClusterBoundaryNode exit = new ClusterBoundaryNode(hash, MetaNodeType.CLUSTER_EXIT);
				IndexedClusterNode exitId = nodesByCluster.get(fromNodeId.cluster).addNode(exit);
				establishEdgeSet(fromNodeId.cluster).add(new RawEdge(fromNodeId, exitId, type, ordinal));
				// }
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
		if ((absoluteTag >= ClusterNode.SYSCALL_SINGLETON_START) && (absoluteTag < ClusterNode.SYSCALL_SINGLETON_END)) {
			IndexedClusterNode nodeId = syscallSingletons.get(absoluteTag);
			if (nodeId == null) {
				RawClusterData nodeData = establishNodeData(ConfiguredSoftwareDistributions.SYSTEM_CLUSTER);
				ClusterBasicBlock node = new ClusterBasicBlock(SoftwareModule.SYSTEM_MODULE, absoluteTag, 0, 0L,
						MetaNodeType.SINGLETON);
				nodeId = nodeData.addNode(node);
				syscallSingletons.put(absoluteTag, nodeId);
			}
			return nodeId;
		}

		ModuleInstance moduleInstance;
		AutonomousSoftwareDistribution cluster;
		if ((absoluteTag == ClusterNode.PROCESS_ENTRY_SINGLETON) || (absoluteTag == ClusterNode.SYSTEM_SINGLETON)) {
			moduleInstance = ModuleInstance.SYSTEM;
			cluster = ConfiguredSoftwareDistributions.SYSTEM_CLUSTER;
		} else if ((absoluteTag >= ClusterNode.BLACK_BOX_SINGLETON_START)
				&& (absoluteTag < ClusterNode.BLACK_BOX_SINGLETON_END)) {
			return nodesByRawTag.get(new RawTag(absoluteTag, tagVersion));
		} else {
			moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
			if (moduleInstance == null)
				return null;
			if (moduleInstance.unit.isAnonymous)
				cluster = ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER;
			else
				cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit.get(moduleInstance.unit);
		}

		ClusterModule clusterModule = null;
		RawClusterData nodeData = nodesByCluster.get(cluster);
		if (nodeData == null) {
			toString();
		} else {
			clusterModule = nodeData.moduleList.getModule(moduleInstance.unit);
		}

		// ClusterModule clusterModule = nodesByCluster.get(cluster).moduleList.getModule(moduleInstance.unit);

		if (moduleInstance.unit.isAnonymous) {
			IndexedClusterNode node = nodesByRawTag.get(new RawTag(absoluteTag, tagVersion));
			return node;
		}

		long tag = (absoluteTag - moduleInstance.start);
		ClusterBasicBlock.Key key = new ClusterBasicBlock.Key(clusterModule, tag, tagVersion);
		RawClusterData clusterData = nodesByCluster.get(cluster);
		if (clusterData == null)
			return null;
		IndexedClusterNode node = clusterData.getNode(key);
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
