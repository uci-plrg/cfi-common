package edu.uci.eecs.crowdsafe.common.io;

import java.io.IOException;
import java.io.RandomAccessFile;

public class LittleEndianRandomAccessFile {
	private final RandomAccessFile file;

	public LittleEndianRandomAccessFile(RandomAccessFile file) {
		this.file = file;
	}

	public void seek(long position) throws IOException {
		file.seek(position);
	}

	public void writeInt(int data) throws IOException {
		data = ((data & 0xff) << 0x18) | ((data & 0xff00) << 0x8) | ((data & 0xff0000) >> 0x8) | ((data & 0xff000000) >> 0x18);
		file.writeInt(data);
	}

	public void writeLong(long data) throws IOException {
		data = ((data & 0xffL) << 0x38L) | ((data & 0xff00L) << 0x28L) | ((data & 0xff0000L) << 0x18L)
				| ((data & 0xff000000L) << 0x8L) | ((data & 0xff00000000L) >> 0x8L)
				| ((data & 0xff0000000000L) >> 0x18L) | ((data & 0xff000000000000L) >> 0x28L)
				| ((data & 0xff00000000000000L) >> 0x38L);
		file.writeLong(data);
	}
	
	public void close() throws IOException {
		file.close();
	}
}
