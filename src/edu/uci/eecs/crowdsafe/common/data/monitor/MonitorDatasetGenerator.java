package edu.uci.eecs.crowdsafe.common.data.monitor;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterBasicBlock;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader.ClusterGraphLoadSession;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianRandomAccessFile;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDirectory;

public class MonitorDatasetGenerator {

	private static class Pointer {
		int pointerLocation;
		int offsetLocation;
	}

	private static class CatalogPointers {
		Pointer name = new Pointer();
		Pointer data = new Pointer();
	}

	private static class OrderByRelativeTag implements Comparator<ClusterBasicBlock> {
		@Override
		public int compare(ClusterBasicBlock first, ClusterBasicBlock second) {
			return (first.getRelativeTag() - second.getRelativeTag());
		}
	}

	private static class OrderByModuleName implements Comparator<AutonomousSoftwareDistribution> {
		@Override
		public int compare(AutonomousSoftwareDistribution first, AutonomousSoftwareDistribution second) {
			return first.getUnits().iterator().next().name.compareTo(second.getUnits().iterator().next().name);
		}
	}

	private static final Charset ascii = Charset.forName("US-ASCII");

	private final File clusterDataDirectory;
	private final File outputFile;

	private final ClusterTraceDataSource dataSource;
	private final ClusterGraphLoadSession loadSession;

	private final Map<SoftwareUnit, CatalogPointers> catalogPointers = new LinkedHashMap<SoftwareUnit, CatalogPointers>();
	private final Map<ClusterBasicBlock, Pointer> nodePointers = new LinkedHashMap<ClusterBasicBlock, Pointer>();

	private final List<AutonomousSoftwareDistribution> sortedClusters;;

	private final LittleEndianCursorWriter writer;

	public MonitorDatasetGenerator(File clusterDataDirectory, File outputFile) throws IOException {
		this.clusterDataDirectory = clusterDataDirectory;
		this.outputFile = outputFile;

		dataSource = new ClusterTraceDirectory(clusterDataDirectory).loadExistingFiles();
		loadSession = new ClusterGraphLoadSession(dataSource);

		OrderByModuleName nameOrder = new OrderByModuleName();
		sortedClusters = new ArrayList<AutonomousSoftwareDistribution>(dataSource.getReprsentedClusters());
		Collections.sort(sortedClusters, nameOrder);

		writer = new LittleEndianCursorWriter(outputFile);
	}

	public void generateDataset() throws IOException {
		generateCatalog();
		generateNameBlock();
		generateModules();

		writer.conclude();

		insertOffsets();
	}

	private void generateCatalog() throws IOException {
		for (AutonomousSoftwareDistribution cluster : sortedClusters) {
			if (cluster.getUnitCount() > 1)
				throw new UnsupportedOperationException("Monitor dataset currently only supports singleton clusters.");

			SoftwareUnit unit = cluster.getUnits().iterator().next();
			CatalogPointers unitPointers = new CatalogPointers();
			unitPointers.name.pointerLocation = writer.getCursor();
			writer.writeInt(0);
			unitPointers.data.pointerLocation = writer.getCursor();
			writer.writeInt(0);

			catalogPointers.put(unit, unitPointers);
		}
	}

	private void generateNameBlock() throws IOException {
		for (AutonomousSoftwareDistribution cluster : sortedClusters) {
			SoftwareUnit unit = cluster.getUnits().iterator().next();
			CatalogPointers unitPointers = catalogPointers.get(unit);
			unitPointers.name.offsetLocation = writer.getCursor();

			writer.writeString(unit.name, ascii);
		}
		writer.alignData(4);
	}

	private void generateModules() throws IOException {
		List<ClusterBasicBlock> nodeSorter = new ArrayList<ClusterBasicBlock>();
		OrderByRelativeTag sortOrder = new OrderByRelativeTag();
		List<Edge<ClusterNode<?>>> intraModule = new ArrayList<Edge<ClusterNode<?>>>();
		List<Edge<ClusterNode<?>>> callSites = new ArrayList<Edge<ClusterNode<?>>>();
		List<Edge<ClusterNode<?>>> exports = new ArrayList<Edge<ClusterNode<?>>>();
		int moduleStart;

		for (AutonomousSoftwareDistribution cluster : sortedClusters) {
			nodeSorter.clear();
			SoftwareUnit unit = cluster.getUnits().iterator().next();
			CatalogPointers unitPointers = catalogPointers.get(unit);
			unitPointers.data.offsetLocation = writer.getCursor();
			moduleStart = writer.getCursor();

			ModuleGraphCluster<?> graph = loadSession.loadClusterGraph(cluster);
			for (Node<?> node : graph.getAllNodes()) {
				if ((node.getType() == MetaNodeType.NORMAL) || (node.getType() == MetaNodeType.RETURN))
					nodeSorter.add((ClusterBasicBlock) node);
			}
			Collections.sort(nodeSorter, sortOrder);

			for (ClusterBasicBlock node : nodeSorter) {
				writer.writeInt(node.getRelativeTag());

				Pointer nodePointer = new Pointer();
				nodePointer.pointerLocation = writer.getCursor();
				nodePointers.put(node, nodePointer);

				writer.writeInt(0);
			}

			int edgeCountWord;
			int intraModuleWord;
			for (ClusterBasicBlock node : nodeSorter) {
				Pointer nodePointer = nodePointers.get(node);
				nodePointer.offsetLocation = (writer.getCursor() - moduleStart);

				intraModule.clear();
				callSites.clear();
				exports.clear();

				OrdinalEdgeList<ClusterNode<?>> edges = node.getOutgoingEdges();
				try {
					for (Edge<ClusterNode<?>> edge : edges) {
						if (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT) {
							callSites.add(edge);
						} else {
							intraModule.add(edge);
						}
					}
				} finally {
					edges.release();
				}

				edges = node.getIncomingEdges();
				try {
					for (Edge<ClusterNode<?>> edge : edges) {
						if (edge.getFromNode().getType() == MetaNodeType.CLUSTER_ENTRY) {
							exports.add(edge);
						}
					}
				} finally {
					edges.release();
				}

				edgeCountWord = intraModule.size();
				edgeCountWord |= (callSites.size() << 0x8);
				edgeCountWord |= (exports.size() << 0x14);
				writer.writeInt(edgeCountWord);

				writer.writeLong(node.getHash());
				for (Edge<ClusterNode<?>> edge : intraModule) {
					intraModuleWord = edge.getToNode().getRelativeTag() & 0xffffff;
					intraModuleWord |= (edge.getOrdinal() << 0x18);
					writer.writeInt(intraModuleWord);
				}
				for (Edge<ClusterNode<?>> edge : callSites) {
					writer.writeLong(edge.getToNode().getHash());
				}
				for (Edge<ClusterNode<?>> edge : exports) {
					writer.writeLong(edge.getFromNode().getHash());
				}
			}
		}

	}

	private void insertOffsets() throws IOException {
		LittleEndianRandomAccessFile offsetAccess = new LittleEndianRandomAccessFile(new RandomAccessFile(outputFile,
				"rw"));

		for (CatalogPointers catalogPointer : catalogPointers.values()) {
			offsetAccess.seek(catalogPointer.name.pointerLocation);
			offsetAccess.writeInt(catalogPointer.name.offsetLocation);
			offsetAccess.seek(catalogPointer.data.pointerLocation);
			offsetAccess.writeInt(catalogPointer.data.offsetLocation);
		}

		for (Pointer nodePointer : nodePointers.values()) {
			offsetAccess.seek(nodePointer.pointerLocation);
			offsetAccess.writeInt(nodePointer.offsetLocation);
		}

		offsetAccess.close();
	}
}
