package com.wqwu.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NioDemo {

	private final static Logger log = LoggerFactory.getLogger(NioDemo.class);

	public static void main(String[] args) throws IOException {
		log.info("app started");
		final NioManager nioManager = new NioManager();
	    final String host = "www.google.com";
	    final int port = 80;
	    final NioBuffer nioBuffer = new NioBuffer();
	    NioHandler handler = new NioHandler() {
	        @Override
	        public void onConnected(NioTcpClient client) throws Exception {
	            log.info("{} was connected", client);
	            String getRequest = "GET / HTTP/1.0\r\nHost: " + host + "\r\nUser-Agent: NioClient 1.0\r\n\r\n";
	            byte[] data = getRequest.getBytes(Charset.forName("UTF-8"));
	            client.write(data);
	        }

	        @Override
	        public void onDisconnected(NioTcpClient client) throws Exception {
	            log.info("{} was disconnected", client);
	            byte[] bytes = nioBuffer.readBytes(nioBuffer.readableByteSize());
	            String str = new String(bytes, Charset.forName("GBK"));
	            nioBuffer.clear();
	            log.info(str);
	            nioManager.shutdown();
	        }

	        @Override
	        public void onDataReceived(NioTcpClient client, ByteBuffer buffer)
	                throws Exception {
	            log.info("received {} bytes by {}", buffer.remaining(), client);
	            nioBuffer.addBuffer(buffer);
	        }

			@Override
			public void onExceptionHappened(NioTcpClient client, Exception e)
					throws Exception {
				log.error("", e);
	            client.disconnect();
			}

	    };
	    NioTcpClient socket = new NioTcpClient(nioManager, handler);
	    socket.connect(host, port);
	}

}
