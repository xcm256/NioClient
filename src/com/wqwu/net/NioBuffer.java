package com.wqwu.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class NioBuffer {
	
	private List<ByteBuffer> bufferList = new ArrayList<ByteBuffer>();
	
	public int readableByteSize() {
		int size = 0;
		for (int index = 0; index < bufferList.size(); index++) {
			size += bufferList.get(index).remaining();
		}
		return size;
	}
	
	public byte[] readBytes(int length) throws IOException {
		if (length > readableByteSize()) {
			throw new IOException("read length exceeded NioBuffer readableByteSize");
		}
		byte[] bytes = new byte[length];
		int readIndex = 0;
		int remain = length;
		while (true) {
			ByteBuffer buffer = bufferList.get(0);
			final int readLength = Math.min(remain, buffer.remaining());
			buffer.get(bytes, readIndex, readLength);
			readIndex += readLength;
			remain -= readLength;
			if (buffer.remaining() <= 0)
				bufferList.remove(0);
			if (remain == 0)
				break;
		}
		return bytes;
	}
	
	public void addBuffer(ByteBuffer buffer) {
		bufferList.add(buffer);
	}
	
	public void clear() {
		bufferList.clear();
	}
}
