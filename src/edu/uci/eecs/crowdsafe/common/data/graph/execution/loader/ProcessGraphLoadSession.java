package edu.uci.eecs.crowdsafe.common.data.graph.execution.loader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.ProcessTraceStreamType;
import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.exception.InvalidTagException;

public class ProcessGraphLoadSession {

	public enum LoadTarget {
		NODE("node"),
		EDGE("edge"),
		CROSS_MODULE_EDGE("cross-module edge");

		public final String displayName;

		private LoadTarget(String displayName) {
			this.displayName = displayName;
		}
	}

	public interface LoadEventListener {
		void nodeLoadReference(long tag, long hash, LoadTarget target);

		void nodeLoadReference(Node node, LoadTarget target);

		void nodeCreation(Node node);

		void graphAddition(Node node, ModuleGraphCluster cluster);

		void edgeCreation(Edge edge);
	}

	public interface ExecutionNodeCollection {
		void add(ExecutionNode node);
	}

	public void loadNodes(ProcessTraceDataSource dataSource, ExecutionNodeCollection collection,
			ProcessExecutionModuleSet modules) throws IOException {
		ProcessGraphNodeFactory nodeFactory = new ProcessGraphNodeFactory(modules,
				dataSource.getLittleEndianInputStream(ProcessTraceStreamType.GRAPH_HASH));

		try {
			while (nodeFactory.ready()) {
				collection.add(nodeFactory.createNode());
			}
		} finally {
			nodeFactory.close();
		}
	}

	public ProcessExecutionGraph loadGraph(ProcessTraceDataSource dataSource, LoadEventListener listener)
			throws IOException {
		GraphLoader graphLoader = new GraphLoader(dataSource, listener);
		return graphLoader.loadGraph();
	}

	class GraphLoader {
		final ProcessTraceDataSource dataSource;
		final LoadEventListener listener;

		final Map<ExecutionNode.Key, ExecutionNode> hashLookupTable = new HashMap<ExecutionNode.Key, ExecutionNode>();
		ProcessExecutionGraph graph;

		GraphLoader(ProcessTraceDataSource dataSource, LoadEventListener listener) {
			this.dataSource = dataSource;
			this.listener = listener;
		}

		ProcessExecutionGraph loadGraph() throws IOException {
			ProcessModuleLoader moduleLoader = new ProcessModuleLoader();
			ProcessExecutionModuleSet modules = moduleLoader.loadModules(dataSource);
			graph = new ProcessExecutionGraph(dataSource, modules);

			try {
				loadGraphNodes(modules);
				readIntraModuleEdges();
				readCrossModuleEdges();
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new InvalidGraphException(e);
			}

			graph.trimEmptyClusters();

			// Some other initialization and sanity checks
			for (ModuleGraphCluster cluster : graph.getAutonomousClusters()) {
				cluster.getGraphData().validate();
				cluster.findUnreachableNodes();
			}

			return graph;
		}

		private void loadGraphNodes(ProcessExecutionModuleSet modules) throws IOException {
			ProcessGraphNodeFactory nodeFactory = new ProcessGraphNodeFactory(modules,
					dataSource.getLittleEndianInputStream(ProcessTraceStreamType.GRAPH_HASH));
			try {
				if (nodeFactory.ready()) {
					ExecutionNode node = nodeFactory.createNode();
					addNodeToGraph(node);
					createEntryPoint(node);
				}

				while (nodeFactory.ready()) {
					ExecutionNode node = nodeFactory.createNode();
					addNodeToGraph(node);
				}
			} finally {
				nodeFactory.close();
			}
		}

		// 12% hot during load!
		private void addNodeToGraph(ExecutionNode node) {
			if (listener != null)
				listener.nodeCreation(node);

			// Tags don't duplicate in lookup file
			if (hashLookupTable.containsKey(node.getKey())) {
				ExecutionNode existingNode = hashLookupTable.get(node.getKey());
				if ((existingNode.getHash() != node.getHash())
						&& (node.getModule().unit != SoftwareDistributionUnit.UNKNOWN)
						&& (existingNode.getModule().unit != SoftwareDistributionUnit.UNKNOWN)) {
					String msg = String.format("Duplicate tags: %s -> %s in datasource %s", node.getKey(),
							existingNode, dataSource.toString());
					throw new InvalidTagException(msg);
				}
				return;
			}

			ModuleGraphCluster moduleCluster = graph.getModuleGraphCluster(node.getModule().unit);
			ModuleGraph moduleGraph = moduleCluster.getModuleGraph(node.getModule().unit);
			if (moduleGraph == null) {
				moduleGraph = new ModuleGraph(graph, node.getModule().unit, node.getModule().version);
				moduleCluster.addModule(moduleGraph);
			}
			moduleCluster.addNode(node);
			hashLookupTable.put(node.getKey(), node);

			if (listener != null)
				listener.graphAddition(node, moduleCluster);
		}

		private void createEntryPoint(ExecutionNode node) {
			ExecutionNode entryNode = graph.getModuleGraphCluster(node.getModule().unit).addClusterEntryNode(1L,
					node.getModule(), node.getTimestamp());
			Edge<ExecutionNode> clusterEntryEdge = new Edge<ExecutionNode>(entryNode, node, EdgeType.MODULE_ENTRY, 0);
			entryNode.addOutgoingEdge(clusterEntryEdge);
			node.addIncomingEdge(clusterEntryEdge);

			if (listener != null)
				listener.edgeCreation(clusterEntryEdge);
		}

		private void readIntraModuleEdges() throws IOException {
			ProcessGraphEdgeFactory edgeFactory = new ProcessGraphEdgeFactory(this,
					dataSource.getLittleEndianInputStream(ProcessTraceStreamType.MODULE_GRAPH));

			try {
				while (edgeFactory.ready())
					edgeFactory.createEdge();
			} finally {
				edgeFactory.close();
			}
		}

		private void readCrossModuleEdges() throws IOException {
			ProcessGraphCrossModuleEdgeFactory crossModuleEdgeFactory = new ProcessGraphCrossModuleEdgeFactory(this,
					dataSource.getLittleEndianInputStream(ProcessTraceStreamType.CROSS_MODULE_GRAPH));

			try {
				while (crossModuleEdgeFactory.ready()) {
					crossModuleEdgeFactory.createEdge();
				}
			} finally {
				crossModuleEdgeFactory.close();
			}
		}
	}
}
