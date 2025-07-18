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
	
	public synchronized void start(int listenPort) {
		start(listenPort, ServerSocketFactory.getDefault());
	}
	
	public synchronized void start(int listenPort, ServerSocketFactory serverSocketFactory) {
		this.stopping = false;
                Thread.startVirtualThread(new ServerProcess(listenPort, serverSocketFactory));
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
			LOGGER.debug("SOCKS server started...");
			try {
				handleClients(port);
				LOGGER.debug("SOCKS server stopped...");
			} catch (IOException e) {
				LOGGER.debug("SOCKS server crashed...");
				Thread.currentThread().interrupt();
			}
		}

		protected void handleClients(int port) throws IOException {
			final ServerSocket listenSocket = serverSocketFactory.createServerSocket(port);
			listenSocket.setSoTimeout(SocksConstants.LISTEN_TIMEOUT);
			
			LOGGER.debug("SOCKS server listening at port: " + listenSocket.getLocalPort());

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
				LOGGER.debug("Connection from : " + Utils.getSocketInfo(clientSocket));
                                Thread.startVirtualThread(new ProxyHandler(clientSocket));
			} catch (InterruptedIOException e) {
				//	This exception is thrown when accept timeout is expired
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
	}
}