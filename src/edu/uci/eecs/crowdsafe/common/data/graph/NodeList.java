package edu.uci.eecs.crowdsafe.common.data.graph;

public interface NodeList<NodeType extends Node<NodeType>> {
	int size();

	boolean isSingleton();

	NodeType get(int index);
}
