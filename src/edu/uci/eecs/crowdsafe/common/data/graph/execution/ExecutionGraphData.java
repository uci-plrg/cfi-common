package edu.uci.eecs.crowdsafe.common.data.graph.execution;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareDistributionUnit;
import edu.uci.eecs.crowdsafe.common.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.log.Log;

public class ExecutionGraphData {
	public final ProcessExecutionGraph containingGraph;

	public final NodeHashMap nodesByHash = new NodeHashMap();

	public final Map<ExecutionNode.Key, ExecutionNode> nodesByKey = new HashMap<ExecutionNode.Key, ExecutionNode>();

	public ExecutionGraphData(ProcessExecutionGraph containingGraph) {
		this.containingGraph = containingGraph;
	}

	public ExecutionNode HACK_relativeTagLookup(ExecutionNode foreignNode) {
		if (foreignNode.getTagVersion() > 0)
			return null; // no way to find it across versions

		SoftwareDistributionUnit foreignUnit = foreignNode.getModule().unit;
		Iterable<ModuleInstance> localModules = containingGraph.getModules()
				.getModule(foreignUnit);
		for (ModuleInstance localModule : localModules) {
			long localTag = localModule.start + foreignNode.getRelativeTag();
			ExecutionNode node = nodesByKey.get(ExecutionNode.Key.create(
					localTag, 0, localModule));
			if (node != null)
				return node;
		}
		return null;
	}

	/**
	 * To validate the correctness of the graph. Basically it checks if entry points have no incoming edges, exit points
	 * have no outgoing edges. It might include more validation stuff later...
	 * 
	 * @return true means this is a valid graph, otherwise it's invalid
	 */
	public void validate() {
		for (ExecutionNode node : nodesByKey.values()) {
			switch (node.getType()) {
				case PROCESS_ENTRY:
					if (node.getIncomingEdges().size() != 0) {
						throw new InvalidGraphException(
								"Exit point has outgoing edges!");
					}
					break;
				case PROCESS_EXIT:
					if (node.getOutgoingEdges().size() != 0) {
						Log.log("");
						throw new InvalidGraphException(
								"Exit point has outgoing edges!");
					}
					break;
				default:
					break;
			}
		}
	}
}
