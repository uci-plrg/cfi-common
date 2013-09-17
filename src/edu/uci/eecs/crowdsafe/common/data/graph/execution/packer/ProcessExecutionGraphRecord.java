package edu.uci.eecs.crowdsafe.common.data.graph.execution.packer;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;

interface ProcessExecutionGraphRecord {

	abstract class Factory {
		protected final LittleEndianInputStream input;
		private final int recordSize;

		Factory(LittleEndianInputStream input, int recordSize) {
			this.input = input;
			this.recordSize = recordSize;
		}

		abstract ProcessExecutionGraphRecord createRecord() throws IOException;

		boolean hasMoreRecords() throws IOException {
			return input.ready(recordSize);
		}
	}

	static class TwoWordFactory extends Factory {
		TwoWordFactory(LittleEndianInputStream input) {
			super(input, 2);
		}

		@Override
		public ProcessExecutionGraphRecord createRecord() throws IOException {
			return new TwoWordRecord(input.readLong(), input.readLong());
		}
	}

	static class ThreeWordFactory extends Factory {
		ThreeWordFactory(LittleEndianInputStream input) {
			super(input, 3);
		}

		@Override
		public ProcessExecutionGraphRecord createRecord() throws IOException {
			return new ThreeWordRecord(input.readLong(), input.readLong(), input.readLong());
		}
	}

	abstract class Writer<RecordType extends ProcessExecutionGraphRecord> {
		protected final LittleEndianOutputStream output;

		protected Writer(LittleEndianOutputStream output) {
			this.output = output;
		}

		abstract void writeRecord(RecordType record) throws IOException;
		
		void flush() throws IOException {
			output.flush();
		}
	}

	static class TwoWordWriter extends Writer<TwoWordRecord> {
		TwoWordWriter(LittleEndianOutputStream output) {
			super(output);
		}

		@Override
		void writeRecord(TwoWordRecord record) throws IOException {
			output.writeLong(record.first);
			output.writeLong(record.second);
		}
	}

	static class ThreeWordWriter extends Writer<ThreeWordRecord> {
		ThreeWordWriter(LittleEndianOutputStream output) {
			super(output);
		}

		@Override
		void writeRecord(ThreeWordRecord record) throws IOException {
			output.writeLong(record.first);
			output.writeLong(record.second);
			output.writeLong(record.third);
		}
	}

	static class TwoWordRecord implements ProcessExecutionGraphRecord {
		final long first, second;
		final int hash;

		private TwoWordRecord(long first, long second) {
			this.first = first;
			this.second = second;

			final int prime = 31;
			int hashing = 1;
			hashing = prime * hashing + (int) (first ^ (first >>> 32));
			this.hash = prime * hashing + (int) (second ^ (second >>> 32));
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TwoWordRecord other = (TwoWordRecord) obj;
			if (first != other.first)
				return false;
			if (second != other.second)
				return false;
			return true;
		}
	}

	static class ThreeWordRecord implements ProcessExecutionGraphRecord {
		final long first, second, third;
		final int hash;

		private ThreeWordRecord(long first, long second, long third) {
			this.first = first;
			this.second = second;
			this.third = third;

			final int prime = 31;
			int hashing = 1;
			hashing = prime * hashing + (int) (first ^ (first >>> 32));
			hashing = prime * hashing + (int) (second ^ (second >>> 32));
			this.hash = prime * hashing + (int) (third ^ (third >>> 32));
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ThreeWordRecord other = (ThreeWordRecord) obj;
			if (first != other.first)
				return false;
			if (second != other.second)
				return false;
			if (third != other.third)
				return false;
			return true;
		}
	}
}
