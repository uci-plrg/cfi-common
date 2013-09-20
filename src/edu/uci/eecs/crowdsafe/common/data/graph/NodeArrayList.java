package edu.uci.eecs.crowdsafe.common.data.graph;

import java.util.ArrayList;

public class NodeArrayList<NodeType extends Node<NodeType>> extends ArrayList<NodeType> implements NodeList<NodeType> {

	@Override
	public boolean isSingleton() {
		return false;
	}
}
