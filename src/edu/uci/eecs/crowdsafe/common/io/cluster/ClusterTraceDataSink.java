package edu.uci.eecs.crowdsafe.common.io.cluster;

import java.io.IOException;
import java.io.OutputStream;

import edu.uci.eecs.crowdsafe.common.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;

public interface ClusterTraceDataSink {

	void addCluster(AutonomousSoftwareDistribution cluster, String filenameFormat);
	
	OutputStream getDataOutputStream(AutonomousSoftwareDistribution cluster, ClusterTraceStreamType streamType)
			throws IOException;

	LittleEndianOutputStream getLittleEndianOutputStream(AutonomousSoftwareDistribution cluster,
			ClusterTraceStreamType streamType) throws IOException;
}
