package edu.uci.eecs.crowdsafe.common.data.graph;

public interface NodeList {
	int size();

	boolean isSingleton();

	Node get(int index);
}
