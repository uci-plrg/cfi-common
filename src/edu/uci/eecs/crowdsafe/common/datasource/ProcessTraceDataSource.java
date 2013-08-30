package edu.uci.eecs.crowdsafe.common.datasource;

import java.io.IOException;
import java.io.InputStream;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;

public interface ProcessTraceDataSource {
	int getProcessId();

	String getProcessName();

	InputStream getDataInputStream(ProcessTraceStreamType streamType) throws IOException;

	LittleEndianInputStream getLittleEndianInputStream(ProcessTraceStreamType streamType) throws IOException;
}
