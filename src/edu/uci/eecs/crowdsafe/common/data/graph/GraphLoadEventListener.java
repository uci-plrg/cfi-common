package edu.uci.eecs.crowdsafe.common.data.graph;

public interface GraphLoadEventListener {

	public enum LoadTarget {
		NODE("node"),
		EDGE("edge"),
		CROSS_MODULE_EDGE("cross-module edge");

		public final String displayName;

		private LoadTarget(String displayName) {
			this.displayName = displayName;
		}
	}

	void nodeLoadReference(long tag, long hash, LoadTarget target);

	void nodeLoadReference(Node<?> node, LoadTarget target);

	void nodeCreation(Node<?> node);

	void graphAddition(Node<?> node, ModuleGraphCluster<?> cluster);

	void edgeCreation(Edge<?> edge);
}
