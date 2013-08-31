package com.wqwu.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NioDemo {

	private final static Logger log = LoggerFactory.getLogger(NioDemo.class);

	public static void main(String[] args) throws IOException {
		log.info("app started");
		
		NioManager.instance().init();
		
		final String host = "www.yahoo.com";
		final int port = 80;
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.reset();
		NioHandler handler = new NioHandler() {
			@Override
			public void onConnected(NioTcpClient client) throws Exception {
				log.info("{} was connected", client);
				String getRequest = "GET / HTTP/1.0\r\nHost: www.yahoo.com\r\nUser-Agent: NioClient 1.0\r\n\r\n";
				byte[] data = getRequest.getBytes(Charset.forName("UTF-8"));
				client.write(data);
			}

			@Override
			public void onDisconnected(NioTcpClient client) throws Exception {
				log.info("{} was disconnected", client);
				String str = new String(out.toByteArray(), Charset.forName("UTF-8"));
				out.close();
				log.info(str);
				NioManager.instance().shutdown();
			}

			@Override
			public void onDataReceived(NioTcpClient client, ByteBuffer buffer)
					throws Exception {
				log.info("received {} bytes by {}", buffer.remaining(), client);
				out.write(buffer.array());
			}

			@Override
			public void exceptionCaught(NioTcpClient client, Exception e)
					throws Exception {
				log.error("", e);
				client.disconnect();
			}
			
		};
		NioTcpClient socket = new NioTcpClient(handler);
		socket.connect(host, port);
		
	}

}
