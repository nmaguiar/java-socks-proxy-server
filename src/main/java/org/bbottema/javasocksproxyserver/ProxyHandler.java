package org.bbottema.javasocksproxyserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.String.format;
import static org.bbottema.javasocksproxyserver.Utils.getSocketInfo;

public class ProxyHandler implements Runnable {

	//private static final Logger LOGGER = LoggerFactory.getLogger(ProxyHandler.class);

	private InputStream m_ClientInput = null;
	private OutputStream m_ClientOutput = null;
	private InputStream m_ServerInput = null;
	private OutputStream m_ServerOutput = null;
	private ReentrantLock m_lock;
	private Socks4Impl comm = null;

	Socket m_ClientSocket;
	Socket m_ServerSocket = null;
	byte[] m_Buffer = new byte[SocksConstants.DEFAULT_BUF_SIZE];

	public ProxyHandler(Socket clientSocket) {
		m_lock = new ReentrantLock();
		m_ClientSocket = clientSocket;
		try {
			m_ClientSocket.setSoTimeout(SocksConstants.DEFAULT_PROXY_TIMEOUT);
		} catch (SocketException e) {
			SocksServer.callback.error("Socket Exception during seting Timeout.");
		}
		SocksServer.callback.debug("Proxy Created.");
	}

	public void setLock(ReentrantLock lock) {
		this.m_lock = lock;
	}

	public void run() {
		SocksServer.callback.debug("Proxy Started.");
		setLock(new ReentrantLock());

		if (prepareClient()) {
			processRelay();
			close();
		} else {
			SocksServer.callback.error("Proxy - client socket is null !");
		}
	}

	public void close() {
		try {
			if (m_ClientOutput != null) {
				m_ClientOutput.flush();
				m_ClientOutput.close();
			}
		} catch (IOException e) {
			// ignore
		}
		try {
			if (m_ServerOutput != null) {
				m_ServerOutput.flush();
				m_ServerOutput.close();
			}
		} catch (IOException e) {
			// ignore
		}

		try {
			if (m_ClientSocket != null) {
				m_ClientSocket.close();
			}
		} catch (IOException e) {
			// ignore
		}

		try {
			if (m_ServerSocket != null) {
				m_ServerSocket.close();
			}
		} catch (IOException e) {
			// ignore
		}

		m_ServerSocket = null;
		m_ClientSocket = null;

		SocksServer.callback.debug("Proxy Closed.");
	}

	public void sendToClient(byte[] buffer) {
		sendToClient(buffer, buffer.length);
	}

	public void sendToClient(byte[] buffer, int len) {
		if (m_ClientOutput != null && len > 0 && len <= buffer.length) {
			try {
				m_ClientOutput.write(buffer, 0, len);
				m_ClientOutput.flush();
			} catch (IOException e) {
				SocksServer.callback.error("Sending data to client");
			}
		}
	}

	public void sendToServer(byte[] buffer, int len) {
		if (m_ServerOutput != null && len > 0 && len <= buffer.length) {
			try {
				m_ServerOutput.write(buffer, 0, len);
				m_ServerOutput.flush();
			} catch (IOException e) {
				SocksServer.callback.error("Sending data to server");
			}
		}
	}

	public boolean isActive() {
		return m_ClientSocket != null && m_ServerSocket != null;
	}

	public void connectToServer(String server, int port) throws IOException {

		if (server.equals("")) {
			close();
			SocksServer.callback.error("Invalid Remote Host Name - Empty String !!!");
			return;
		}

		m_ServerSocket = new Socket(server, port);
		m_ServerSocket.setSoTimeout(SocksConstants.DEFAULT_PROXY_TIMEOUT);

		SocksServer.callback.debug("Connected to " + getSocketInfo(m_ServerSocket));
		prepareServer();
	}

	protected void prepareServer() throws IOException {
		m_lock.lock();
		try {
			m_ServerInput = new BufferedInputStream(m_ServerSocket.getInputStream());
			m_ServerOutput = new BufferedOutputStream(m_ServerSocket.getOutputStream());
		} finally {
			m_lock.unlock();
		}
	}

	public boolean prepareClient() {
		if (m_ClientSocket == null) return false;

		try {
			m_ClientInput = new BufferedInputStream(m_ClientSocket.getInputStream());
			m_ClientOutput = new BufferedOutputStream(m_ClientSocket.getOutputStream());
			return true;
		} catch (IOException e) {
			SocksServer.callback.error("Proxy - can't get I/O streams!");
			SocksServer.callback.error(e.getMessage(), e);
			return false;
		}
	}

	public void processRelay() {
		try {
			byte SOCKS_Version = getByteFromClient();

			switch (SOCKS_Version) {
				case SocksConstants.SOCKS4_Version:
					comm = new Socks4Impl(this);
					break;
				case SocksConstants.SOCKS5_Version:
					comm = new Socks5Impl(this);
					break;
				default:
					SocksServer.callback.error("Invalid SOCKS version : " + SOCKS_Version);
					return;
			}
			SocksServer.callback.debug("Accepted SOCKS " + SOCKS_Version + " Request.");

			comm.authenticate(SOCKS_Version);
			comm.getClientCommand();

			switch (comm.socksCommand) {
				case SocksConstants.SC_CONNECT:
					comm.connect();
					relay();
					break;

				case SocksConstants.SC_BIND:
					comm.bind();
					relay();
					break;

				case SocksConstants.SC_UDP:
					comm.udp();
					break;
			}
		} catch (Exception e) {
			SocksServer.callback.error(e.getMessage(), e);
		}
	}

	public byte getByteFromClient() throws Exception {
		while (m_ClientSocket != null) {
			int b;
			try {
				b = m_ClientInput.read();
			} catch (InterruptedIOException e) {
                                Thread.onSpinWait();
				continue;
			}
			return (byte) b; // return loaded byte
		}
		throw new Exception("Interrupted Reading GetByteFromClient()");
	}

	public void relay() {
		boolean isActive = true;

		while (isActive) {

			//---> Check for client data <---

			int dlen = checkClientData();

			if (dlen < 0) {
				isActive = false;
			}
			if (dlen > 0) {
				logData(dlen, "Cli data");
				sendToServer(m_Buffer, dlen);
			}

			//---> Check for Server data <---
			dlen = checkServerData();

			if (dlen < 0) isActive = false;
			if (dlen > 0) {
				logData(dlen, "Srv data");
				sendToClient(m_Buffer, dlen);
			}

                        Thread.onSpinWait();
		}
	}

	public int checkClientData() {
		m_lock.lock();
		try {
			//	The client side is not opened.
			if (m_ClientInput == null) return -1;

			int dlen;

			try {
				dlen = m_ClientInput.read(m_Buffer, 0, SocksConstants.DEFAULT_BUF_SIZE);
			} catch (InterruptedIOException e) {
				return 0;
			} catch (IOException e) {
				SocksServer.callback.debug("Client connection Closed!");
				close();    //	Close the server on this exception
				return -1;
			}

			if (dlen < 0) close();

			return dlen;
		} finally {
			m_lock.unlock();
		}
	}

	public int checkServerData() {
		m_lock.lock();
		try {
			//	The client side is not opened.
			if (m_ServerInput == null) return -1;

			int dlen;

			try {
				dlen = m_ServerInput.read(m_Buffer, 0, SocksConstants.DEFAULT_BUF_SIZE);
			} catch (InterruptedIOException e) {
				return 0;
			} catch (IOException e) {
				SocksServer.callback.debug("Server connection Closed!");
				close();    //	Close the server on this exception
				return -1;
			}

			if (dlen < 0) {
				close();
			}

			return dlen;
		} finally {
			m_lock.unlock();
		}
	}

	private void logData(final int traffic, final String dataSource) {
		SocksServer.callback.debug(format("%s : %s >> <%s/%s:%d> : %d bytes.",
				dataSource,
				getSocketInfo(m_ClientSocket),
				comm.m_ServerIP.getHostName(),
				comm.m_ServerIP.getHostAddress(),
				comm.m_nServerPort, traffic));
	}

	public int getPort() {
		return m_ServerSocket.getPort();
	}
}