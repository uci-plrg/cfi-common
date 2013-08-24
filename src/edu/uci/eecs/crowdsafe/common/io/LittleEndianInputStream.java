package edu.uci.eecs.crowdsafe.common.io;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import edu.uci.eecs.crowdsafe.common.log.Log;

public class LittleEndianInputStream {
	private static final int BUFFER_SIZE = 1 << 14;

	private final InputStream input;

	private int end = -1;
	private int byteIndex = -1;
	byte data[] = new byte[BUFFER_SIZE];

	public LittleEndianInputStream(InputStream input) {
		this.input = input;
	}

	public boolean ready() throws IOException {
		return (byteIndex < end) || (input.available() > 0);
	}

	public long readLong() throws IOException {
		if ((end < 0) || (byteIndex == BUFFER_SIZE)) {
			end = Math.min(input.available(), BUFFER_SIZE);
			input.read(data, 0, end);
			byteIndex = 0;
		}

		long value = ((((long) data[byteIndex + 7]) & 0xffL) << 0x38)
				| ((((long) data[byteIndex + 6]) & 0xffL) << 0x30)
				| ((((long) data[byteIndex + 5]) & 0xffL) << 0x28)
				| ((((long) data[byteIndex + 4]) & 0xffL) << 0x20)
				| ((((long) data[byteIndex + 3]) & 0xffL) << 0x18)
				| ((((long) data[byteIndex + 2]) & 0xffL) << 0x10)
				| ((((long) data[byteIndex + 1]) & 0xffL) << 0x8)
				| (((long) data[byteIndex]) & 0xffL);
		byteIndex += 8;
		return value;
	}

	public void close() throws IOException {
		input.close();
	}

	// unit test
	public static void main(String[] args) {
		try {
			byte[] buffer = new byte[] { 0x10, 0x32, 0x54, 0x76, (byte) 0x98,
					(byte) 0xba, (byte) 0xdc, (byte) 0xfe, 0x10, 0x32, 0x54,
					0x76, (byte) 0x98, (byte) 0xba, (byte) 0xdc, (byte) 0xfe,
					0x10, 0x32, 0x54, 0x76, (byte) 0x98, (byte) 0xba,
					(byte) 0xdc, (byte) 0xfe, 0x10, 0x32, 0x54, 0x76,
					(byte) 0x98, (byte) 0xba, (byte) 0xdc, (byte) 0xfe, 0x10,
					0x32, 0x54, 0x76, (byte) 0x98, (byte) 0xba, (byte) 0xdc,
					(byte) 0xfe, (byte) 0xef, (byte) 0xcd, (byte) 0xab,
					(byte) 0x89, 0x67, 0x45, 0x23, 0x01, (byte) 0xef,
					(byte) 0xcd, (byte) 0xab, (byte) 0x89, 0x67, 0x45, 0x23,
					0x01, (byte) 0xef, (byte) 0xcd, (byte) 0xab, (byte) 0x89,
					0x67, 0x45, 0x23, 0x01 };

			ByteArrayInputStream input = new ByteArrayInputStream(buffer);
			LittleEndianInputStream test = new LittleEndianInputStream(input);

			while (test.ready()) {
				long reversed = test.readLong();
				Log.log("LittleEndianInputStream read value 0x%x", reversed);
			}

			input.reset();
			DataInputStream dataInput = new DataInputStream(input);
			while (dataInput.available() > 0) {
				long forward = dataInput.readLong();
				Log.log("DataInputStream read value 0x%x", forward);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}