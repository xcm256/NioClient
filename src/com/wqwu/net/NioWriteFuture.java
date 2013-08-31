package com.wqwu.net;

import java.util.concurrent.ConcurrentLinkedQueue;

public class NioWriteFuture {
	
	private final NioTcpClient client;
	private boolean isDone = false;
	private boolean isSuccess = false;
	
	private final ConcurrentLinkedQueue<NioWriteFutureListener> listeners = new ConcurrentLinkedQueue<NioWriteFutureListener>();
	
	public NioWriteFuture(NioTcpClient client) {
		this.client = client;
	}
	
	public NioTcpClient getClient() {
		return client;
	}
	
	public boolean isSuccess() {
		return isSuccess;
	}

	public void setSuccess(boolean isSuccess) {
		this.isSuccess = isSuccess;
	}
	
	public boolean isDone() {
		return isDone;
	}

	public void setDone(boolean isDone) {
		this.isDone = isDone;
	}
	
	public void addListener(NioWriteFutureListener listener) {
		listeners.add(listener);
	}
	
	public void notifyListeners() throws Exception {
		for (NioWriteFutureListener listener : listeners) {
			listener.operationComplete(this);
		}
	}
}
