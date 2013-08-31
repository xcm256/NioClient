package com.wqwu.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NioTcpClient {
	
	private final static Logger log = LoggerFactory.getLogger(NioTcpClient.class);
	private final static AtomicLong gClientId = new AtomicLong(0);
	
	private final List<NioWriteUnit> pendingWriteUnits = new LinkedList<NioWriteUnit>();
	private final long clientId;
    private String host = "";
    private int port;
    private SocketChannel socketChannel;
    private final NioHandler handler;

	public NioTcpClient(NioHandler handler) {
		clientId = gClientId.incrementAndGet();
    	this.handler = handler;
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
    
	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public SocketChannel getSocketChannel() {
		return socketChannel;
	}
	
	public void setSocketChannel(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}
	
	public boolean isConnected() {
		return this.socketChannel != null && this.socketChannel.isConnected();
	}
	
	public void exceptionCaught(Exception e) throws Exception {
		if (handler != null) {
			handler.exceptionCaught(this, e);
		}
	}
	
	public void handleConnected() throws Exception {
		if (handler != null) {
			handler.onConnected(this);
		}
	}
	
	public void handleDisconnected() throws Exception {
		if (handler != null) {
			handler.onDisconnected(this);
		}
	}
	
	public void handleDataReceived(ByteBuffer buffer) throws Exception {
		if (handler != null) {
			handler.onDataReceived(this, buffer);
		}
	}
	
	public void connect(String host, int port)  {
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
		NioManager.instance().connect(this);
	}
	
	public void disconnect() {
		synchronized(pendingWriteUnits) {
			pendingWriteUnits.clear();
		}
		NioManager.instance().disconnect(this);
	}
	
	public NioWriteFuture write(byte[] data) {
		NioWriteFuture future = new NioWriteFuture(this);
		ByteBuffer buffer = ByteBuffer.wrap(data);
		NioWriteUnit unit = new NioWriteUnit(future, buffer);
		synchronized(pendingWriteUnits) {
			pendingWriteUnits.add(unit);
		}
		NioManager.instance().write(this);
		return future;
	}
	
	public NioWriteUnit getOneWriteUnit() {
		synchronized(pendingWriteUnits) {
			if (pendingWriteUnits.size() <= 0)
				return null;
			else
				return pendingWriteUnits.remove(0);
		}
	}
	
	public void putBackWriteUnitOnTop(NioWriteUnit unit) {
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
