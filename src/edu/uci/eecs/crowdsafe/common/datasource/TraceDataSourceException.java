package edu.uci.eecs.crowdsafe.common.datasource;

public class TraceDataSourceException extends RuntimeException {
	public TraceDataSourceException(String message) {
		super(message);
	}

	public TraceDataSourceException(String message, Exception source) {
		super(message, source);
	}
}
