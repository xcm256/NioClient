package com.wqwu.net;


public interface NioWriteFutureListener {
	public void operationComplete(NioWriteFuture future) throws Exception;
}
