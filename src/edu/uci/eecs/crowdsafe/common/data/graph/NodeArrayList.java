package edu.uci.eecs.crowdsafe.common.data.graph;

import java.util.ArrayList;

public class NodeArrayList extends ArrayList<Node> implements NodeList {

	@Override
	public boolean isSingleton() {
		return false;
	}
}
