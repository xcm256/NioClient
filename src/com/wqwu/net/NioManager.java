package com.wqwu.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// references: 
// 1. http://rox-xmlrpc.sourceforge.net/niotut/
// 2. http://today.java.net/article/2007/02/08/architecture-highly-scalable-nio-based-server

public class NioManager {
	
	private final static Logger log = LoggerFactory.getLogger(NioManager.class);
	private static NioManager instance = null;
	
	public static NioManager instance() {
		if (instance != null)
			return instance;
		synchronized(log) {
			if (instance != null)
				return instance;
			instance = new NioManager();
		}
		return instance;
	}
	
	private static enum SelectorChangeType {
		SelectorChangeTypeRegister,
		SelectorChangeTypeChange,
		SelectorChangeTypeCancel
	}
	
	private static class SelectorChange {
		private final NioTcpClient client;
		private final SelectorChangeType type;
		private final int ops;
		public NioTcpClient getClient() {
			return client;
		}
		public SelectorChangeType getType() {
			return type;
		}
		public int getOps() {
			return ops;
		}
		public SelectorChange(NioTcpClient client, SelectorChangeType type, int ops) {
			this.client = client;
			this.type = type;
			this.ops = ops;
		}
	}
	
	private static abstract class NioTask {
		
		private final NioTcpClient client;
		
		public NioTcpClient getClient() {
			return client;
		}

		public NioTask(NioTcpClient client) {
			this.client = client;
		}
		
		public abstract void run() throws Exception;
	}
	
	private static class NioThreadFactoryBuilder {
		public static ThreadFactory newThreadFactory(String threadPrefix) {
			NioThreadFactoryBuilder builder = new NioThreadFactoryBuilder(threadPrefix);
			return builder.build();
		}
		
		private final String threadPrefix;
		private final AtomicLong seq = new AtomicLong(0);
		
		private NioThreadFactoryBuilder(String threadPrefix) {
			this.threadPrefix = threadPrefix;
		}
		
		public ThreadFactory build() {
			ThreadFactory factory = new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					Thread t = Executors.defaultThreadFactory().newThread(r);
					t.setName(threadPrefix + "-" + seq.incrementAndGet());
					return t;
				}
			};
			return factory;
		}
	}
	
	private class SelectorTask implements Runnable {
		
		@Override
        public void run() {
            while (true) {
            	handleSelectorChanges();
            	if (isShutdown.get())
            		break;
            	log.info("start to select");
                try {
					selector.select();
				} catch (IOException e) {
					log.error("selector.select()", e);
				}
                Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isConnectable()) {
                        this.handleConnect(key);
                    } else if (key.isReadable()) {
                        this.handleRead(key);
                    } else if (key.isWritable()) {
                        this.handleWrite(key);
                    }
                }
            }
        }

		private void handleSelectorChanges() {
			synchronized(selectorChanges) {
				for (SelectorChange change : selectorChanges) {
					switch (change.getType()) {
					case SelectorChangeTypeRegister:
					{
						handleSelectorRegister(change);
					}
						break;
					case SelectorChangeTypeChange:
					{
						final NioTcpClient client = change.getClient();
						SelectionKey key = client.getSocketChannel().keyFor(selector);
						if (key != null) {
							key.interestOps(change.getOps());
						}
					}
						break;
					case SelectorChangeTypeCancel:
					{
						final NioTcpClient client = change.getClient();
						SelectionKey key = client.getSocketChannel().keyFor(selector);
						if (key != null) {
							key.cancel();
						}
						doDisconnect(client);
					}
						break;
					}
				}
				selectorChanges.clear();
			}
		}

		private void handleSelectorRegister(SelectorChange change) {
			final NioTcpClient client = change.getClient();
			try {
				SelectionKey key = client.getSocketChannel().register(selector, change.getOps());
				if (key != null) {
					key.attach(client);
				}
			} catch (final ClosedChannelException e) {
				log.error("register socket channel", e);
				post(new NioTask(client) {
					@Override
					public void run() throws Exception {
						client.exceptionCaught(e);
					}
				});
			}
		}
        
        private void handleConnect(SelectionKey key) {
            key.interestOps(SelectionKey.OP_READ);
            NioTcpClient client = (NioTcpClient)key.attachment();
            try {
				client.getSocketChannel().finishConnect();
				post(new NioTask(client) {
					@Override
					public void run() throws Exception {
						this.getClient().handleConnected();
					}
				});
			} catch (IOException e) {
				log.error(client + " handleConnect()", e);
				key.cancel();
				doDisconnect(client);
			}
        }
        
        private void handleRead(SelectionKey key) {
        	NioTcpClient client = (NioTcpClient)key.attachment();
        	SocketChannel socketChannel = client.getSocketChannel();
        	if (socketChannel == null) {
        		return;
        	}
        	readBuffer.clear();
        	int readCount = -1;
        	try {
				readCount = socketChannel.read(readBuffer);
			} catch (IOException e) {
				log.error(client + " handleRead", e);
				key.cancel();
				doDisconnect(client);
				return;
			}
        	if (readCount == -1) {
        		key.cancel();
        		doDisconnect(client);
				return;
        	}
        	byte[] tmpBuffer = new byte[readCount];
        	System.arraycopy(readBuffer.array(), 0, tmpBuffer, 0, readCount);
        	final ByteBuffer tmpByteBuffer = ByteBuffer.wrap(tmpBuffer);
        	post(new NioTask(client) {
				@Override
				public void run() throws Exception {
					this.getClient().handleDataReceived(tmpByteBuffer);
				}
        	});
        }
        
        private void handleWrite(SelectionKey key) {
        	final NioTcpClient client = (NioTcpClient)key.attachment();
        	SocketChannel socketChannel = client.getSocketChannel();
        	if (socketChannel == null) {
        		return;
        	}
        	final ByteBuffer buffer = client.getOneWriteBuffer();
        	if (buffer == null) {
        		key.interestOps(SelectionKey.OP_READ);
        		return;
        	}
        	try {
				socketChannel.write(buffer);
				if (buffer.remaining() > 0) {
					client.putBackOneWriteBufferOnTop(buffer);
				}
			} catch (final IOException e) {
				log.error("handleWrite", e);
				post(new NioTask(client) {
					@Override
					public void run() throws Exception {
						client.exceptionCaught(e);
					}
				});
			}
        }
    };
	
	private final ExecutorService workerThread = Executors.newSingleThreadExecutor(
            NioThreadFactoryBuilder.newThreadFactory("NioClientIoWorker"));
	
	private final ExecutorService selectorThread = Executors.newSingleThreadExecutor(
    		NioThreadFactoryBuilder.newThreadFactory("NioClientSelector"));
	
	private ByteBuffer readBuffer = ByteBuffer.allocate(8192);
	private Selector selector;
	private final AtomicBoolean isShutdown = new AtomicBoolean(false);
	private final List<SelectorChange> selectorChanges = new LinkedList<SelectorChange>();
	private final SelectorTask selectorTask = new SelectorTask();
    
	public void init() throws IOException {
		if (!isShutdown.get()) {
			selector = SelectorProvider.provider().openSelector();
			selectorThread.execute(selectorTask);
		} else {
			log.warn("NioManager shutdown is already done.");
		}
	}
	
	public void shutdown() {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				if (isShutdown.compareAndSet(false, true)) {
					shutdown(workerThread);
					selector.wakeup();
					shutdown(selectorThread);
				}
			}
		});
		t.start();
	}
	
	private static void shutdown(ExecutorService executor) {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
				executor.shutdownNow();
				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
					log.error("thread {} did not terminate", executor);
				}
			}
		} catch (InterruptedException ie) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
	
	public void connect(final NioTcpClient client) {
		log.info("start to connect {}:{} for {}", client.getHost(), client.getPort(), client);
		post(new NioTask(client) {
			@Override
			public void run() throws Exception {
				SocketChannel socketChannel = SocketChannel.open();
				client.setSocketChannel(socketChannel);
				socketChannel.configureBlocking(false);
				socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
				socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
				SocketAddress address = new InetSocketAddress(client.getHost(), client.getPort());
				socketChannel.connect(address);
				synchronized(selectorChanges) {
					SelectorChange change = new SelectorChange(client, SelectorChangeType.SelectorChangeTypeRegister, SelectionKey.OP_CONNECT);
					selectorChanges.add(change);
					selector.wakeup();
				}
			}
		});
	}
	
	public void disconnect(NioTcpClient client) {
		post(new NioTask(client) {
			@Override
			public void run() throws Exception {
				synchronized(selectorChanges) {
					SelectorChange change = new SelectorChange(this.getClient(), SelectorChangeType.SelectorChangeTypeCancel, 0);
					selectorChanges.add(change);
					selector.wakeup();
				}
			}
		});
	}
	
	public void write(NioTcpClient client) {
		post(new NioTask(client) {
			@Override
			public void run() throws Exception {
				synchronized(selectorChanges) {
					SelectorChange change = new SelectorChange(this.getClient(), SelectorChangeType.SelectorChangeTypeChange, SelectionKey.OP_WRITE);
					selectorChanges.add(change);
					selector.wakeup();
				}
			}
		});
	}
	
	private void doDisconnect(NioTcpClient client) {
		post(new NioTask(client) {
			@Override
			public void run() throws Exception {
				SocketChannel socketChannel = this.getClient().getSocketChannel();
				if (socketChannel == null)
					return;
				if (socketChannel.isConnected()) {
					socketChannel.close();
				}
				this.getClient().setSocketChannel(null);
				this.getClient().handleDisconnected();
			}
		});
	}
	
	private void post(final NioTask task) {
		workerThread.execute(new Runnable() {
			@Override
			public void run() {
				try {
					task.run();
				} catch (Exception e) {
					log.error("NioTask.run()", e);
					try {
						task.getClient().exceptionCaught(e);
					} catch (Exception eat) {
						log.error("NioTcpClient.exceptionCaught()", eat);
						throw new RuntimeException(eat);
					}
				}
			}
		});
	}
}
