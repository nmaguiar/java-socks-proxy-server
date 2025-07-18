package org.bbottema.javasocksproxyserver;

import org.junit.Test;

import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.*;

public class SyncSocksServerTest {


    @Test
    public void simple_start_stop() {
        SyncSocksServer server = new SyncSocksServer();
        int port = Utils.getFreePort();
        server.start(port);
        server.stop();
    }

    @Test
    public void cant_start_on_the_same_port() {
        SyncSocksServer server = new SyncSocksServer();
        SyncSocksServer server2 = new SyncSocksServer(1,100,1);
        int port = Utils.getFreePort();
        server.start(port);
        assertThrows(
                RuntimeException.class,
                () -> server2.start(port)
        );
        server.stop();
    }

    @Test
    public void socksServer_available_to_connect_right_after_start_method_completes() {
        SyncSocksServer server = new SyncSocksServer();
        int port = Utils.getFreePort();
        server.start(port);
        // socket should be available for connection right away
        assertTrue(Utils.isLocalPortAvailableToConnect(port));
        server.stop();
    }

    @Test
    public void start_stop_two_times() {
        SyncSocksServer server = new SyncSocksServer();
        int port = Utils.getFreePort();
        server.start(port);
        assertTrue(Utils.isLocalPortAvailableToConnect(port));
        server.stop();
        // after closing Server Socket, it's not available immediately for new Server Socket
        server.start(port);
        server.stop();
    }

    @Test
    public void hang_connection_doesn_t_prevent_from_stop() throws IOException {
        SyncSocksServer server = new SyncSocksServer();
        int port = Utils.getFreePort();
        server.start(port);
        Socket socket = new Socket("localhost", port);

        server.stop();
        socket.close();
    }

    @Test
    public void socks4_connects_and_relays() throws Exception {
        try (ServerSocket echo = new ServerSocket(0)) {
            Thread echoThread = Thread.startVirtualThread(() -> {
                try (Socket s = echo.accept()) {
                    int b = s.getInputStream().read();
                    s.getOutputStream().write(b);
                } catch (IOException ignored) {
                }
            });

            SyncSocksServer proxy = new SyncSocksServer();
            int proxyPort = Utils.getFreePort();
            proxy.start(proxyPort);

            try (Socket client = new Socket("localhost", proxyPort)) {
                client.setSoTimeout(1000);
                OutputStream out = client.getOutputStream();
                InputStream in = client.getInputStream();
                int destPort = echo.getLocalPort();
                byte[] request = new byte[] {
                        0x04, 0x01,
                        (byte) (destPort >> 8), (byte) destPort,
                        127, 0, 0, 1,
                        0x00
                };
                out.write(request);
                out.flush();
                in.readNBytes(8);

                out.write(55);
                assertEquals(55, in.read());
            }

            proxy.stop();
            echoThread.join(1000);
        }
    }

    @Test
    public void socks5_connects_and_relays() throws Exception {
        try (ServerSocket echo = new ServerSocket(0)) {
            Thread echoThread = Thread.startVirtualThread(() -> {
                try (Socket s = echo.accept()) {
                    int b = s.getInputStream().read();
                    s.getOutputStream().write(b);
                } catch (IOException ignored) {
                }
            });

            SyncSocksServer proxy = new SyncSocksServer();
            int proxyPort = Utils.getFreePort();
            proxy.start(proxyPort);

            try (Socket client = new Socket("localhost", proxyPort)) {
                client.setSoTimeout(1000);
                OutputStream out = client.getOutputStream();
                InputStream in = client.getInputStream();

                out.write(new byte[] {0x05, 0x01, 0x00});
                out.flush();
                in.readNBytes(2);

                int destPort = echo.getLocalPort();
                byte[] req = new byte[] {
                        0x05, 0x01, 0x00, 0x01,
                        127, 0, 0, 1,
                        (byte) (destPort >> 8), (byte) destPort
                };
                out.write(req);
                out.flush();
                in.readNBytes(10);

                out.write(11);
                assertEquals(11, in.read());
            }

            proxy.stop();
            echoThread.join(1000);
        }
    }

}