package edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata;

import edu.uci.eecs.crowdsafe.common.data.results.Graph;

public class ClusterUIBInterval {

	public enum Type {
		TOTAL,
		ADMITTED,
		SUSPICIOUS;

		public Graph.EvaluationType getResultType() {
			switch (this) {
				case TOTAL:
					return Graph.EvaluationType.TOTAL;
				case ADMITTED:
					return Graph.EvaluationType.ADMITTED;
				case SUSPICIOUS:
					return Graph.EvaluationType.SUSPICIOUS;
			}
			return null;
		}

		static Type forId(int id) {
			switch (id) {
				case 0:
					return Type.TOTAL;
				case 1:
					return ADMITTED;
				case 2:
					return SUSPICIOUS;
			}
			return null;
		}
	}

	public final Type type;
	public final int span; // log10 of the interval
	public final int count;
	public final int maxConsecutive;

	public ClusterUIBInterval(int typeId, int span, int count, int maxConsecutive) {
		this.type = Type.forId(typeId);
		this.span = span;
		this.count = count;
		this.maxConsecutive = maxConsecutive;
	}
}
