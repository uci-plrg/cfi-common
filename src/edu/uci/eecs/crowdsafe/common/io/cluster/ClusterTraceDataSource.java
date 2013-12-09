package edu.uci.eecs.crowdsafe.common.io.cluster;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;

public interface ClusterTraceDataSource {
	
	File getDirectory();
	
	Collection<AutonomousSoftwareDistribution> getReprsentedClusters();
	
	String parseTraceName();
	
	InputStream getDataInputStream(AutonomousSoftwareDistribution cluster, ClusterTraceStreamType streamType)
			throws IOException;

	LittleEndianInputStream getLittleEndianInputStream(AutonomousSoftwareDistribution cluster,
			ClusterTraceStreamType streamType) throws IOException;
}
