package org.bbottema.javasocksproxyserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocksServer {
	private final ExecutorService pool;
	private static final Logger LOGGER = LoggerFactory.getLogger(SocksServer.class);

	protected boolean stopping = false;
	public static Callback callback = null;

	public SocksServer() {
		int _c = Runtime.getRuntime().availableProcessors() * 2;
		this.pool = Executors.newFixedThreadPool(_c > 2 ? _c : 2);
	}

	public SocksServer(int _cores) {
		this.pool = Executors.newFixedThreadPool(_cores);
	}

	public synchronized void start(int listenPort) {
		if (SocksServer.callback == null) SocksServer.callback = new CallbackImpl(LOGGER);
		start(listenPort, ServerSocketFactory.getDefault());
	}

	public synchronized void start(int listenPort, ServerSocketFactory serverSocketFactory) {
		if (SocksServer.callback == null) SocksServer.callback = new CallbackImpl(LOGGER);
		this.stopping = false;
		pool.execute(new ServerProcess(listenPort, serverSocketFactory));
	}

	public synchronized void start(int listenPort, Callback callback) {
		SocksServer.callback = callback;
		start(listenPort, ServerSocketFactory.getDefault());
	}

	public synchronized void start(int listenPort, ServerSocketFactory serverSocketFactory, Callback callback) {
		SocksServer.callback = callback;
		this.stopping = false;
		pool.execute(new ServerProcess(listenPort, serverSocketFactory));
	}

	public synchronized void stop() {
		stopping = true;
		pool.shutdown();
		try {
			// Wait a while for existing tasks to terminate
			if (!pool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
				pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!pool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
					LOGGER.error("Pool did not terminate");
				}
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			pool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}

	private class ServerProcess implements Runnable {

		protected final int port;
		private final ServerSocketFactory serverSocketFactory;

		public ServerProcess(int port, ServerSocketFactory serverSocketFactory) {
			this.port = port;
			this.serverSocketFactory = serverSocketFactory;
		}

		@Override
		public void run() {
			SocksServer.callback.debug("SOCKS server started...");
			try {
				handleClients(port);
				SocksServer.callback.debug("SOCKS server stopped...");
			} catch (IOException e) {
				SocksServer.callback.debug("SOCKS server crashed...");
				Thread.currentThread().interrupt();
			}
		}

		protected void handleClients(int port) throws IOException {
			final ServerSocket listenSocket = serverSocketFactory.createServerSocket(port);
			listenSocket.setSoTimeout(SocksConstants.LISTEN_TIMEOUT);

			SocksServer.callback.debug("SOCKS server listening at port: " + listenSocket.getLocalPort());

			while (true) {
				synchronized (SocksServer.this) {
					if (stopping) {
						break;
					}
				}
				handleNextClient(listenSocket);
			}

			try {
				listenSocket.close();
			} catch (IOException e) {
				// ignore
			}
		}

		private void handleNextClient(ServerSocket listenSocket) {
			try {
				final Socket clientSocket = listenSocket.accept();
				clientSocket.setSoTimeout(SocksConstants.DEFAULT_SERVER_TIMEOUT);
				SocksServer.callback.debug("Connection from : " + Utils.getSocketInfo(clientSocket));
				new Thread(new ProxyHandler(clientSocket)).start();
			} catch (InterruptedIOException e) {
				// This exception is thrown when accept timeout is expired
			} catch (Exception e) {
				SocksServer.callback.error(e.getMessage(), e);
			}
		}
	}
}