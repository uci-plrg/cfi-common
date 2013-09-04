package edu.uci.eecs.crowdsafe.common.test;

import java.io.File;
import java.io.FileOutputStream;

import com.google.protobuf.CodedOutputStream;

import edu.uci.eecs.crowdsafe.common.data.results.Statistics.IntegerStatistic;

public class ProtocolBufferTest {

	public static void main(String[] args) {
		try {
			File statFile = new File("/home/b/workspace/temp/integer.dat");
			FileOutputStream out = new FileOutputStream(statFile);
			CodedOutputStream coded = CodedOutputStream.newInstance(out);
			
			IntegerStatistic.Builder builder = IntegerStatistic.newBuilder();
			builder.setId("Count");
			builder.setName("The sum and total count of something");

//			for (int i = 0; i < 1000; i++) {
				builder.setValue(17);
				IntegerStatistic integer = builder.build();

				int size = integer.getSerializedSize();
				coded.writeRawLittleEndian32(size);
				integer.writeTo(coded);
	//		}
			coded.flush();
			out.close();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}