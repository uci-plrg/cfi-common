package edu.uci.eecs.crowdsafe.common.datasource;

import java.io.IOException;
import java.io.InputStream;

public interface ProcessTraceDataSource {
	int getProcessId();

	String getProcessName();

	InputStream getDataInputStream(ProcessTraceStreamType streamType) throws IOException;
}
