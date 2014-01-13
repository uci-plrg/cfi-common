package edu.uci.eecs.crowdsafe.common.data.graph.cluster.metadata;

public class ClusterUIBInterval {

	public enum Type {
		TOTAL,
		ADMITTED,
		SUSPICIOUS;

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
