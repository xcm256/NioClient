package com.wqwu.net;

import java.nio.ByteBuffer;

public class NioWriteUnit {
	
	private final NioWriteFuture future;
	private final ByteBuffer buffer;
	
	public NioWriteUnit(NioWriteFuture future, ByteBuffer buffer) {
		this.future = future;
		this.buffer = buffer;
	}
	
	public ByteBuffer getBuffer() {
		return buffer;
	}
	
	public NioWriteFuture getFuture() {
		return future;
	}
	
}
