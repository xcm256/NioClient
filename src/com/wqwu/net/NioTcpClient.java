package com.wqwu.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NioTcpClient {
	
	private final static Logger log = LoggerFactory.getLogger(NioTcpClient.class);
	private final static AtomicLong gClientId = new AtomicLong(0);
	
	private final ConcurrentHashMap<String, Object> attachments = new ConcurrentHashMap<String, Object>();
	private final List<NioWriteUnit> pendingWriteUnits = new LinkedList<NioWriteUnit>();
	private final long clientId;
	private final NioManager nioManager;
    private final NioHandler handler;
    private String host = "";
    private int port;
    private SocketChannel socketChannel;

	public NioTcpClient(final NioManager nioManager, NioHandler handler) {
		clientId = gClientId.incrementAndGet();
		this.nioManager = nioManager;
    	this.handler = handler;
    }
	
	final public Object putAttachment(String key, Object value) {
		return attachments.put(key, value);
	}
	
	final public Object getAttachment(String key) {
		return attachments.get(key);
	}
    
	private boolean hostIsSame(String other) {
		if (other == null)
			return false;
		else
			return (host.compareTo(other) == 0);
	}
	
	private boolean isSame(String other, int port) {
		return hostIsSame(other) && this.port == port;
	}
    
	final public String getHost() {
		return host;
	}

	final public int getPort() {
		return port;
	}

	final public SocketChannel getSocketChannel() {
		return socketChannel;
	}
	
	final public void setSocketChannel(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}
	
	final public boolean isConnected() {
		return this.socketChannel != null && this.socketChannel.isConnected();
	}
	
	final public void handleException(Exception e) throws Exception {
		if (handler != null) {
			handler.onExceptionHappened(this, e);
		}
	}
	
	final public void handleConnected() throws Exception {
		if (handler != null) {
			handler.onConnected(this);
		}
	}
	
	final public void handleDisconnected() throws Exception {
		if (handler != null) {
			handler.onDisconnected(this);
		}
	}
	
	final public void handleDataReceived(ByteBuffer buffer) throws Exception {
		if (handler != null) {
			handler.onDataReceived(this, buffer);
		}
	}
	
	final public void handleWriteSuccess(NioWriteUnit unit) throws Exception {
		NioWriteFuture future = unit.getFuture();
		future.setDone(true);
		future.setSuccess(true);
		unit.getFuture().notifyListeners();
	}
	
	final public void handleWriteFailure(NioWriteUnit unit, Exception e) throws Exception {
		NioWriteFuture future = unit.getFuture();
		future.setDone(true);
		future.setSuccess(false);
		unit.getFuture().notifyListeners();
		if (handler != null) {
			handler.onExceptionHappened(this, e);
		}
	}
	
	final public void connect(String host, int port)  {
		if (isSame(host, port)) {
			if (socketChannel != null && socketChannel.isConnected()) {
				log.warn("connection of {}:{} is already connected", host, port);
				return;
			}
		} else {
			if (socketChannel != null && socketChannel.isConnected()) {
				this.disconnect();
			}
		}
		this.host = host;
		this.port = port;
		nioManager.connect(this);
	}
	
	final public void disconnect() {
		synchronized(pendingWriteUnits) {
			pendingWriteUnits.clear();
		}
		nioManager.disconnect(this);
	}
	
	final public NioWriteFuture write(byte[] data) throws IOException {
		if (!isConnected()) {
			throw new IOException("connection is not open");
		}
		NioWriteFuture future = new NioWriteFuture(this);
		ByteBuffer buffer = ByteBuffer.wrap(data);
		NioWriteUnit unit = new NioWriteUnit(future, buffer);
		synchronized(pendingWriteUnits) {
			pendingWriteUnits.add(unit);
		}
		nioManager.write(this);
		return future;
	}
	
	final public NioWriteFuture write(NioBuffer buffer) throws IOException {
		if (!isConnected()) {
			throw new IOException("connection is not open");
		}
		byte[] data = buffer.readBytes(buffer.readableByteSize());
		return write(data);
	}
	
	final public NioWriteUnit getOneWriteUnit() {
		synchronized(pendingWriteUnits) {
			if (pendingWriteUnits.size() <= 0)
				return null;
			else
				return pendingWriteUnits.remove(0);
		}
	}
	
	final public void putBackWriteUnitOnTop(NioWriteUnit unit) {
		synchronized(pendingWriteUnits) {
			pendingWriteUnits.add(0, unit);
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append("NioTcpClient-" + clientId);
		if (socketChannel != null && socketChannel.isConnected()) {
			try {
				sb.append(" " + socketChannel.getLocalAddress());
			} catch (IOException e) {
				log.error("getLocalAddress", e);
			}
		}
		sb.append("]");
		return sb.toString();
	}
}
