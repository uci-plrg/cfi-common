package edu.uci.eecs.crowdsafe.common.data.graph.cluster.writer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterGraph;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterModule;
import edu.uci.eecs.crowdsafe.common.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.common.io.cluster.ClusterTraceDataSink;

public class ClusterGraphWriter implements ClusterDataWriter.ClusterData<ClusterNode<?>> {

	private final ClusterGraph graph;

	private final Map<ClusterNode<?>, Integer> nodeIndexMap = new HashMap<ClusterNode<?>, Integer>();
	private final List<Edge<ClusterNode<?>>> allEdges = new ArrayList<Edge<ClusterNode<?>>>();

	private final ClusterDataWriter<ClusterNode<?>> dataWriter;

	public ClusterGraphWriter(ClusterGraph graph, ClusterTraceDataSink dataSink) throws IOException {
		this.graph = graph;

		dataWriter = new ClusterDataWriter<ClusterNode<?>>(this, dataSink);
	}

	public void writeGraph() throws IOException {
		for (ClusterNode<?> node : graph.graph.getAllNodes()) {
			nodeIndexMap.put(node, nodeIndexMap.size());
			dataWriter.writeNode(node);

			for (Edge<ClusterNode<?>> edge : node.getOutgoingEdges()) {
				allEdges.add(edge);
			}
		}

		int edgeIndex = 0;
		Map<Edge<ClusterNode<?>>, Integer> edgeIndexMap = new HashMap<Edge<ClusterNode<?>>, Integer>();
		for (Edge<ClusterNode<?>> edge : allEdges) {
			dataWriter.writeEdge(edge);
			edgeIndexMap.put(edge, edgeIndex++);
		}

		dataWriter.writeMetadataHistory(graph.graph.metadata, edgeIndexMap);
		dataWriter.writeModules();

		dataWriter.flush();
	}

	@Override
	public AutonomousSoftwareDistribution getCluster() {
		return graph.graph.cluster;
	}

	@Override
	public int getModuleIndex(SoftwareModule module) {
		return ((ClusterModule) module).id;
	}

	@Override
	public Iterable<? extends SoftwareModule> getSortedModuleList() {
		return graph.moduleList.sortById();
	}

	@Override
	public int getNodeIndex(ClusterNode<?> node) {
		return nodeIndexMap.get(node);
	}
}
