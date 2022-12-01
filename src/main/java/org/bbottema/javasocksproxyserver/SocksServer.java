package org.bbottema.javasocksproxyserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ServerSocketFactory;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SocksServer {

	private static final Logger LOGGER = LoggerFactory.getLogger(SocksServer.class);
	
	protected boolean stopping = false;
	public static Callback callback = null;
	
	public synchronized void start(int listenPort) {
		if (SocksServer.callback == null) SocksServer.callback = new CallbackImpl(LOGGER);
		start(listenPort, ServerSocketFactory.getDefault());
	}
	
	public synchronized void start(int listenPort, ServerSocketFactory serverSocketFactory) {
		if (SocksServer.callback == null) SocksServer.callback = new CallbackImpl(LOGGER);
		this.stopping = false;
		new Thread(new ServerProcess(listenPort, serverSocketFactory)).start();
	}

	public synchronized	void start(int listenPort, Callback callback) {
		SocksServer.callback = callback;
		start(listenPort, ServerSocketFactory.getDefault());
	}

	public synchronized void start(int listenPort, ServerSocketFactory serverSocketFactory, Callback callback) {
		SocksServer.callback = callback;
		this.stopping = false;
		new Thread(new ServerProcess(listenPort, serverSocketFactory)).start();
	}

	public synchronized void stop() {
		stopping = true;
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
				//	This exception is thrown when accept timeout is expired
			} catch (Exception e) {
				SocksServer.callback.error(e.getMessage(), e);
			}
		}
	}
}