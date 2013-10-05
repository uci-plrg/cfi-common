package edu.uci.eecs.crowdsafe.common.data.graph;

import edu.uci.eecs.crowdsafe.common.data.dist.SoftwareModule;

public interface NodeIdentifier {

	long getHash();

	SoftwareModule getModule();

	int getRelativeTag();
	
	int getInstanceId();

	MetaNodeType getType();
	
	boolean isCallback();
}
