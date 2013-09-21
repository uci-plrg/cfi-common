package edu.uci.eecs.crowdsafe.common.util;

import java.util.List;

import edu.uci.eecs.crowdsafe.common.data.graph.Edge;
import edu.uci.eecs.crowdsafe.common.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.common.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.common.data.graph.Node;
import edu.uci.eecs.crowdsafe.common.data.graph.OrdinalEdgeList;

public class CrowdSafeTraceUtil {

	// Return ordinal of the edge by passing the from tag
	public static int getEdgeOrdinal(long tag) {
		return (int) (tag << 16 >>> 56);
	}

	// Return type of the edge by passing the from tag
	public static EdgeType getTagEdgeType(long tag) {
		return EdgeType.values()[(int) (tag << 8 >>> 56)];
	}

	// Return the effective address of the tag
	public static long getTagEffectiveValue(long tag) {
		return new Long(tag << 24 >>> 24).intValue();
	}

	public static int getNodeVersionNumber(long tag) {
		return new Long(tag >>> 56).intValue();
	}

	public static MetaNodeType getNodeMetaType(long tag) {
		return MetaNodeType.values()[(int) (tag << 8 >>> 56)];
	}

	// get the lower 6 byte of the tag, which is a long integer
	public static long getTag(long annotatedTag) {
		return annotatedTag << 24 >>> 24;
	}

	public static int getTagVersion(long annotatedTag) {
		return (new Long(annotatedTag >>> 56)).intValue();
	}
}
