package com.wqwu.net;

import java.nio.ByteBuffer;

public interface NioHandler {
	public void onConnected(NioTcpClient client) throws Exception;
	public void onDisconnected(NioTcpClient client) throws Exception;
	public void onDataReceived(NioTcpClient client, ByteBuffer buffer) throws Exception;
	public void exceptionCaught(NioTcpClient client, Exception e) throws Exception;
}
