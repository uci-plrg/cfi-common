package edu.uci.eecs.crowdsafe.common.data.graph.transform;

public class RawUnexpectedIndirectBranchInterval {

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

	public static RawUnexpectedIndirectBranchInterval parse(long rawData) {
		int typeId = ((int) ((rawData >> 8) & 0x3L));
		int span = ((int) ((rawData >> 0xa) & 0x3fL));
		int maxConsecutive = ((int) ((rawData >> 0x10) & 0xffffL));
		int count = ((int) ((rawData >> 0x20) & 0xffffffffL));
		return new RawUnexpectedIndirectBranchInterval(Type.forId(typeId), span, count, maxConsecutive);
	}

	public final Type type;
	public final int span;
	public final int count;
	public final int maxConsecutive;

	public RawUnexpectedIndirectBranchInterval(Type type, int span, int count, int maxConsecutive) {
		this.type = type;
		this.span = span;
		this.count = count;
		this.maxConsecutive = maxConsecutive;
	}

}
