package edu.uci.eecs.crowdsafe.common.data.graph.transform;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;

interface RawGraphEntry {

	abstract class Factory<EntryType extends RawGraphEntry> {
		protected final LittleEndianInputStream input;
		private final int entryWordCount;

		Factory(LittleEndianInputStream input, int entryWordCount) {
			this.input = input;
			this.entryWordCount = entryWordCount;
		}

		abstract EntryType createEntry() throws IOException;

		boolean hasMoreEntries() throws IOException {
			return input.ready(entryWordCount * 8);
		}
	}

	static class TwoWordFactory extends Factory<TwoWordEntry> {
		TwoWordFactory(LittleEndianInputStream input) {
			super(input, 2);
		}

		@Override
		TwoWordEntry createEntry() throws IOException {
			return new TwoWordEntry(input.readLong(), input.readLong());
		}
	}

	static class ThreeWordFactory extends Factory<ThreeWordEntry> {
		ThreeWordFactory(LittleEndianInputStream input) {
			super(input, 3);
		}

		@Override
		ThreeWordEntry createEntry() throws IOException {
			return new ThreeWordEntry(input.readLong(), input.readLong(), input.readLong());
		}
	}

	abstract class Writer<EntryType extends RawGraphEntry> {
		protected final LittleEndianOutputStream output;

		protected Writer(LittleEndianOutputStream output) {
			this.output = output;
		}

		abstract void writeEntry(EntryType entry) throws IOException;

		void flush() throws IOException {
			output.flush();
		}
	}

	static class TwoWordWriter extends Writer<TwoWordEntry> {
		TwoWordWriter(LittleEndianOutputStream output) {
			super(output);
		}

		@Override
		void writeEntry(TwoWordEntry entry) throws IOException {
			output.writeLong(entry.first);
			output.writeLong(entry.second);
		}
	}

	static class ThreeWordWriter extends Writer<ThreeWordEntry> {
		ThreeWordWriter(LittleEndianOutputStream output) {
			super(output);
		}

		@Override
		void writeEntry(ThreeWordEntry entry) throws IOException {
			output.writeLong(entry.first);
			output.writeLong(entry.second);
			output.writeLong(entry.third);
		}
	}

	static class TwoWordEntry implements RawGraphEntry {
		final long first, second;
		final int hash;

		private TwoWordEntry(long first, long second) {
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
			TwoWordEntry other = (TwoWordEntry) obj;
			if (first != other.first)
				return false;
			if (second != other.second)
				return false;
			return true;
		}
	}

	static class ThreeWordEntry implements RawGraphEntry {
		final long first, second, third;
		final int hash;

		private ThreeWordEntry(long first, long second, long third) {
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
			ThreeWordEntry other = (ThreeWordEntry) obj;
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