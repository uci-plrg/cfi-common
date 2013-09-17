package edu.uci.eecs.crowdsafe.common.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LittleEndianOutputStream {
	private static final int BUFFER_SIZE = 1 << 14;

	private final OutputStream output;
	public final String description;

	private int byteIndex = 0;
	byte buffer[] = new byte[BUFFER_SIZE];

	public LittleEndianOutputStream(OutputStream output, String description) {
		this.output = output;
		this.description = description;
	}

	public LittleEndianOutputStream(File file) throws FileNotFoundException {
		this.output = new FileOutputStream(file);
		this.description = "file:" + file.getAbsolutePath();
	}

	public void writeLong(long data) throws IOException {
		if (byteIndex == BUFFER_SIZE) {
			output.write(buffer);
			byteIndex = 0;
		}

		buffer[byteIndex++] = (byte) (data);
		buffer[byteIndex++] = (byte) (data >> 0x8);
		buffer[byteIndex++] = (byte) (data >> 0x10);
		buffer[byteIndex++] = (byte) (data >> 0x18);
		buffer[byteIndex++] = (byte) (data >> 0x20);
		buffer[byteIndex++] = (byte) (data >> 0x28);
		buffer[byteIndex++] = (byte) (data >> 0x30);
		buffer[byteIndex++] = (byte) (data >> 0x38);
	}

	public void flush() throws IOException {
		if (byteIndex > 0) {
			output.write(buffer, 0, byteIndex);
			byteIndex = 0;
		}
	}

	public void close() throws IOException {
		output.close();
	}

	// unit test
	public static void main(String[] args) {
		try {
			long forward = 0x123456789abcdefL;
			long backward = 0xfedcba9876543210L;

			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			LittleEndianOutputStream output = new LittleEndianOutputStream(buffer, "byte buffer");

			int count = 9000;
			for (int i = 0; i < count; i++) {
				output.writeLong(forward);
			}
			for (int i = 0; i < count; i++) {
				output.writeLong(backward);
			}
			output.flush();

			System.out.println("Output stream with buffer size " + BUFFER_SIZE + " wrote: ");

			byte written[] = buffer.toByteArray();
			int byteCount = count * 2 * 8;
			for (int i = 0; i < byteCount; i++) {
				System.out.print(String.format("%x", written[i]));
				System.out.print(", ");
				if ((i % 60) == 59)
					System.out.println();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
