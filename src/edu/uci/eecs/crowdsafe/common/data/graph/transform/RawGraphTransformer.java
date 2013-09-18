package edu.uci.eecs.crowdsafe.common.data.graph.transform;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDirectory;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeTraceUtil;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;

public class RawGraphTransformer {

	private final ArgumentStack args;

	private final ProcessModuleLoader executionModuleLoader = new ProcessModuleLoader();

	private ProcessExecutionModuleSet executionModules = null;
	private final Map<AutonomousSoftwareDistribution, ClusterModuleList> clusterModules = new HashMap<AutonomousSoftwareDistribution, ClusterModuleList>();
	
	// can't really use this, use the edge clock
	private final Map<RawExecutionNodeId, ClusterNode> nodesByRawId = new HashMap<RawExecutionNodeId, ClusterNode>(); 
	private final Map<ClusterNode.Key, ClusterNode> nodesByKey = new HashMap<ClusterNode.Key, ClusterNode>();

	public RawGraphTransformer(ArgumentStack args) {
		this.args = args;

		OptionArgumentMap.populateOptions(args);
	}

	private void run() {
		try {
			ConfiguredSoftwareDistributions.initialize();

			while (args.size() > 0) {
				File runDir = new File(args.pop());
				ProcessTraceDirectory dataSource = new ProcessTraceDirectory(runDir,
						ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES);
				transformGraph(dataSource);

				executionModules = null;
				clusterModules.clear();
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
			ClusterModuleList moduleList = clusterModules.get(dist);
			if (moduleList == null) {
				moduleList = new ClusterModuleList();
				clusterModules.put(dist, moduleList);
			}
		}

		transformNodes(dataSource.getLittleEndianInputStream(ProcessTraceStreamType.GRAPH_NODE));
		transformEdges(dataSource.getLittleEndianInputStream(ProcessTraceStreamType.GRAPH_EDGE));
		writeNodes();
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

			ClusterModule clusterModule = clusterModules.get(moduleInstance.unit).establishModule(moduleInstance.unit,
					moduleInstance.version);

			ClusterNode node = new ClusterNode(clusterModule, relativeTag, tagVersion, nodeEntry.second, nodeType);
			nodesByRawId.put(rawNodeId, node);
			nodesByKey.put(node.getKey(), node);
		}
	}

	private void transformEdges(LittleEndianInputStream input) throws IOException {
		RawGraphEntry.TwoWordFactory factory = new RawGraphEntry.TwoWordFactory(input);
		LittleEndianOutputStream output = null;

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

			// write the edge

			ClusterModuleList fromCluster = clusterModules.get(fromNode.getKey().module.unit);
			ClusterModuleList toCluster = clusterModules.get(toNode.getKey().module.unit);
			if (fromCluster != toCluster) {
				// add CMEntry, CMExit to the node maps
				// write edges for them
			}
		}
	}

	private void writeNodes() {
		LittleEndianOutputStream output = null;

		// write nodesByKey.values()
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
