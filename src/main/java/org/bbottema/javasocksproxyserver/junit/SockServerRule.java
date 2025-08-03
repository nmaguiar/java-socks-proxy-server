package org.bbottema.javasocksproxyserver.junit;

import org.bbottema.javasocksproxyserver.SocksServer;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.ExternalResource;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

/**
 * Creates a {@link SocksServer} once, and starts and stops it before and after each test.
 * <p>
 * Can be used both by JUnit's {@code Rule} and {@code ClassRule} (`the latter being the preferred usage).
 */
public class SockServerRule extends ExternalResource {

        private final SocksServer socksServer;
        private final int port;
        private final ServerSocketFactory serverSocketFactory;
        private final SocketFactory socketFactory;

        public SockServerRule(@NotNull Integer port) {
                this(port, ServerSocketFactory.getDefault(), SocketFactory.getDefault());
        }

        public SockServerRule(@NotNull Integer port, @NotNull ServerSocketFactory serverSocketFactory) {
                this(port, serverSocketFactory, SocketFactory.getDefault());
        }

        public SockServerRule(@NotNull Integer port, @NotNull ServerSocketFactory serverSocketFactory, @NotNull SocketFactory socketFactory) {
                this.socksServer = new SocksServer();
                this.port = port;
                this.serverSocketFactory = serverSocketFactory;
                this.socketFactory = socketFactory;
        }

	@Override
	protected void before() {
                this.socksServer.start(port, serverSocketFactory, socketFactory);
	}

	@Override
	protected void after() {
		this.socksServer.stop();
	}
}