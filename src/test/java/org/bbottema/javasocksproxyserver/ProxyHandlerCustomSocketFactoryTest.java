package org.bbottema.javasocksproxyserver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

/**
 * Verifies that {@link ProxyHandler} uses the provided {@link SocketFactory} when connecting to the remote server.
 */
public class ProxyHandlerCustomSocketFactoryTest {

    private ServerSocket serverSocket;
    private Socket serverSideSocket;
    private ExecutorService executor;

    @Before
    public void setup() throws IOException {
        serverSocket = new ServerSocket(0);
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                serverSideSocket = serverSocket.accept();
            } catch (IOException ignored) {
            }
        });

        SocksServer.callback = new Callback() {
            @Override
            public boolean filter(java.net.InetAddress data) { return false; }

            @Override
            public void error(String msg) { }

            @Override
            public void error(String msg, Exception e) { }

            @Override
            public void debug(String msg) { }

            @Override
            public void debug(String msg, Exception e) { }

            @Override
            public void info(String msg) { }
        };
    }

    @After
    public void tearDown() throws Exception {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        if (serverSideSocket != null) {
            serverSideSocket.close();
        }
        serverSocket.close();
    }

    @Test
    public void customSocketFactoryIsUsed() throws Exception {
        final AtomicBoolean used = new AtomicBoolean(false);
        SocketFactory factory = new SocketFactory() {
            private final SocketFactory delegate = SocketFactory.getDefault();

            @Override
            public Socket createSocket() throws IOException {
                return delegate.createSocket();
            }

            @Override
            public Socket createSocket(String host, int port) throws IOException {
                used.set(true);
                return delegate.createSocket(host, port);
            }

            @Override
            public Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws IOException {
                used.set(true);
                return delegate.createSocket(host, port, localHost, localPort);
            }

            @Override
            public Socket createSocket(java.net.InetAddress host, int port) throws IOException {
                used.set(true);
                return delegate.createSocket(host, port);
            }

            @Override
            public Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws IOException {
                used.set(true);
                return delegate.createSocket(address, port, localAddress, localPort);
            }
        };

        ProxyHandler handler = new ProxyHandler(new Socket(), factory);
        handler.connectToServer("localhost", serverSocket.getLocalPort());

        assertTrue("Custom socket factory should have been used", used.get());

        handler.close();
    }
}

