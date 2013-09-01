NioClient
=========

Netty is a brilliant framework for server applications, but it's kind of heavy for client side, NioClient wants to provides serveral light classes to use nonblocking socket.

Some of idea is coming from Netty, but I try to make it simple.

There are also some references (thanks to authors for sharing):

1. http://rox-xmlrpc.sourceforge.net/niotut/
2. http://today.java.net/article/2007/02/08/architecture-highly-scalable-nio-based-server

Thread model
=========
1. there are always two threads running after NioManager.init(), one is selector thread, another is I/O thread.
2. all operations of selector key set will be processed in selector thread.
3. all handler's callback will be called in I/O thread.

Usage
=========
please check NioDemo class as an example, here is copy:

	    NioManager.instance().init();

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
	            String str = new String(bytes, Charset.forName("UTF-8"));
	            nioBuffer.clear();
	            log.info(str);
	            NioManager.instance().shutdown();
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
	    NioTcpClient socket = new NioTcpClient(handler);
	    socket.connect(host, port);
