package edu.uci.eecs.crowdsafe.common.data.graph.cluster.loader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.graph.GraphLoadEventListener;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModuleList;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.common.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.common.datasource.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.common.datasource.cluster.ClusterTraceStreamType;
import edu.uci.eecs.crowdsafe.common.datasource.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;

public class ClusterGraphLoadSession {

	public interface ExecutionNodeCollection {
		void add(ExecutionNode node);
	}

	private final ClusterTraceDataSource dataSource;
	private final ClusterModuleLoader moduleLoader;

	public ClusterGraphLoadSession(ClusterTraceDataSource dataSource) {
		this.dataSource = dataSource;
		moduleLoader = new ClusterModuleLoader(dataSource);
	}

	public void loadNodes(ExecutionTraceDataSource dataSource, ExecutionNodeCollection collection,
			ProcessExecutionModuleSet modules) throws IOException {
		throw new UnsupportedOperationException("Can't load cluster nodes in isolation yet!");

		/**
		 * <pre>
		ProcessGraphNodeFactory nodeFactory = new ProcessGraphNodeFactory(modules,
				dataSource.getLittleEndianInputStream(ExecutionTraceStreamType.GRAPH_NODE));

		try {
			while (nodeFactory.ready()) {
				collection.add(nodeFactory.createNode());
			}
		} finally {
			nodeFactory.close();
		}
		 */
	}

	public ModuleGraphCluster loadClusterGraph(AutonomousSoftwareDistribution cluster) throws IOException {
		GraphLoader graphLoader = new GraphLoader(cluster, null);
		return graphLoader.loadGraph();
	}

	class GraphLoader {
		final AutonomousSoftwareDistribution cluster;

		final GraphLoadEventListener listener;

		ClusterGraph graph;
		final List<ClusterNode> nodeList = new ArrayList<ClusterNode>();

		GraphLoader(AutonomousSoftwareDistribution cluster, GraphLoadEventListener listener) {
			this.cluster = cluster;
			this.listener = listener;

		}

		ModuleGraphCluster loadGraph() throws IOException {
			ClusterModuleList modules = moduleLoader.loadModules(cluster, dataSource);
			graph = new ClusterGraph(cluster, modules);

			try {
				loadGraphNodes(modules);
				loadEdges(modules);
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new InvalidGraphException(e);
			}

			graph.findUnreachableNodes();
			return graph;
		}

		private void loadGraphNodes(ClusterModuleList modules) throws IOException {
			ClusterGraphNodeFactory nodeFactory = new ClusterGraphNodeFactory(modules,
					dataSource.getLittleEndianInputStream(cluster, ClusterTraceStreamType.GRAPH_NODE));
			try {
				while (nodeFactory.ready()) {
					ClusterNode node = nodeFactory.createNode();
					graph.addNode(node);
					nodeList.add(node);

					if (listener != null)
						listener.graphAddition(node, graph);
				}
			} finally {
				nodeFactory.close();
			}
		}

		private void loadEdges(ClusterModuleList modules) throws IOException {
			ClusterGraphEdgeFactory edgeFactory = new ClusterGraphEdgeFactory(graph,
					dataSource.getLittleEndianInputStream(cluster, ClusterTraceStreamType.GRAPH_EDGE));

			try {
				while (edgeFactory.ready())
					edgeFactory.createEdge();
			} finally {
				edgeFactory.close();
			}
		}
	}
}
