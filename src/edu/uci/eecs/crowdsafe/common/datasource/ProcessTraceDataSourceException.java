package edu.uci.eecs.crowdsafe.common.datasource;

public class ProcessTraceDataSourceException extends RuntimeException {
	public ProcessTraceDataSourceException(String message) {
		super(message);
	}

	public ProcessTraceDataSourceException(String message, Exception source) {
		super(message, source);
	}
}
