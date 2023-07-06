/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.pki.keystore.KeyStoreWrapper.KEYSTORE_TYPE_PKCS12;

import org.hyperledger.besu.pki.PkiException;
import org.hyperledger.besu.pki.keystore.HardwareKeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.SoftwareKeyStoreWrapper;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TLSContextFactoryTest {

  // see resources/keys/README.md for setup details.
  private static final String keystorePassword = "test123";

  private static final Logger LOG = LoggerFactory.getLogger(TLSContextFactoryTest.class);

  private static final int MAX_NUMBER_MESSAGES = 10;
  private static final String CRL_PEM = "/keys/crl/crl.pem";
  private static final String PARTNER1_CLIENT1_PKCS11 = "/keys/partner1client1/nss.cfg";
  private static final String PARTNER1_CLIENT1_KEYSTORE = "/keys/partner1client1/client1.p12";
  private static final String PARTNET1_CLIENT2_KEYSTORE = "/keys/partner1client2rvk/client2.p12";
  private static final String PARTNER2_CLIENT1_KEYSTORE = "/keys/partner2client1/client1.p12";
  private static final String PARTNER2_CLIENT2_KEYSTORE = "/keys/partner2client2rvk/client2.p12";
  private static final String INVALIDPARTNER1_CLIENT1_KEYSTORE =
      "/keys/invalidpartner1client1/client1.p12";

  private static final String PARTNER1_CLIENT1_TRUSTSTORE = "/keys/partner1client1/truststore.p12";
  private static final String PARTNET1_CLIENT2_TRUSTSTORE =
      "/keys/partner1client2rvk/truststore.p12";
  private static final String PARTNER2_CLIENT1_TRUSTSTORE = "/keys/partner2client1/truststore.p12";
  private static final String PARTNER2_CLIENT2_TRUSTSTORE =
      "/keys/partner2client2rvk/truststore.p12";
  private static final String INVALIDPARTNER1_CLIENT1_TRUSTSTORE =
      "/keys/invalidpartner1client1/truststore.p12";

  private Server server;
  private Client client;

  static Collection<Object[]> hardwareKeysData() {
    return Arrays.asList(
        new Object[][] {
          {
            "PKCS11 server Partner1 Client1 -> PKCS12 client Partner2 Client1 SuccessfulConnection",
            true,
            getHardwareKeyStoreWrapper(PARTNER1_CLIENT1_PKCS11, CRL_PEM),
            getSoftwareKeyStoreWrapper(
                PARTNER2_CLIENT1_KEYSTORE, PARTNER2_CLIENT1_TRUSTSTORE, CRL_PEM),
            Optional.empty(),
            Optional.empty()
          },
          {
            "PKCS11 server Partner1 Client1 -> PKCS12 client InvalidPartner1 Client1 FailedConnection",
            false,
            getHardwareKeyStoreWrapper(PARTNER1_CLIENT1_PKCS11, CRL_PEM),
            getSoftwareKeyStoreWrapper(
                INVALIDPARTNER1_CLIENT1_KEYSTORE, INVALIDPARTNER1_CLIENT1_TRUSTSTORE, null),
            Optional.empty(),
            Optional.of("certificate_unknown")
          },
          {
            "PKCS11 server Partner1 Client1 -> PKCS12 client Partner2 Client2 revoked FailedConnection",
            false,
            getHardwareKeyStoreWrapper(PARTNER1_CLIENT1_PKCS11, CRL_PEM),
            getSoftwareKeyStoreWrapper(
                PARTNER2_CLIENT2_KEYSTORE, PARTNER2_CLIENT2_TRUSTSTORE, null),
            Optional.of("Certificate revoked"),
            Optional.of("certificate_unknown")
          },
        });
  }

  static Collection<Object[]> softwareKeysData() {
    return Arrays.asList(
        new Object[][] {
          {
            "PKCS12 server Partner1 Client1 -> PKCS12 client Partner2 Client1 SuccessfulConnection",
            true,
            getSoftwareKeyStoreWrapper(
                PARTNER1_CLIENT1_KEYSTORE, PARTNER1_CLIENT1_TRUSTSTORE, CRL_PEM),
            getSoftwareKeyStoreWrapper(
                PARTNER2_CLIENT1_KEYSTORE, PARTNER2_CLIENT1_TRUSTSTORE, CRL_PEM),
            Optional.empty(),
            Optional.empty()
          },
          {
            "PKCS12 server Partner2 Client1 -> PKCS12 client Partner1 Client1 SuccessfulConnection",
            true,
            getSoftwareKeyStoreWrapper(
                PARTNER2_CLIENT1_KEYSTORE, PARTNER2_CLIENT1_TRUSTSTORE, CRL_PEM),
            getSoftwareKeyStoreWrapper(
                PARTNER1_CLIENT1_KEYSTORE, PARTNER1_CLIENT1_TRUSTSTORE, CRL_PEM),
            Optional.empty(),
            Optional.empty()
          },
          {
            "PKCS12 server Partner1 Client1 -> PKCS12 client InvalidPartner1 Client1 FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                PARTNER1_CLIENT1_KEYSTORE, PARTNER1_CLIENT1_TRUSTSTORE, CRL_PEM),
            getSoftwareKeyStoreWrapper(
                INVALIDPARTNER1_CLIENT1_KEYSTORE, INVALIDPARTNER1_CLIENT1_TRUSTSTORE, null),
            Optional.empty(),
            Optional.of("certificate_unknown")
          },
          {
            "PKCS12 server InvalidPartner1 Client1 -> PKCS12 client Partner1 Client1 FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                INVALIDPARTNER1_CLIENT1_KEYSTORE, INVALIDPARTNER1_CLIENT1_TRUSTSTORE, null),
            getSoftwareKeyStoreWrapper(
                PARTNER1_CLIENT1_KEYSTORE, PARTNER1_CLIENT1_TRUSTSTORE, CRL_PEM),
            Optional.of("certificate_unknown"),
            Optional.empty()
          },
          {
            "PKCS12 server Partner1 Client2 (revoked) -> PKCS12 client Partner2 Client1 FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                PARTNET1_CLIENT2_KEYSTORE, PARTNET1_CLIENT2_TRUSTSTORE, null),
            getSoftwareKeyStoreWrapper(
                PARTNER2_CLIENT1_KEYSTORE, PARTNER2_CLIENT1_TRUSTSTORE, CRL_PEM),
            Optional.empty(),
            Optional.of("Certificate revoked")
          },
          {
            "PKCS12 server Partner2 Client1 -> PKCS12 client Partner1 Client2 (revoked) FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                PARTNER2_CLIENT1_KEYSTORE, PARTNER2_CLIENT1_TRUSTSTORE, CRL_PEM),
            getSoftwareKeyStoreWrapper(
                PARTNET1_CLIENT2_KEYSTORE, PARTNET1_CLIENT2_TRUSTSTORE, null),
            Optional.of("Certificate revoked"),
            Optional.of("certificate_unknown")
          },
          {
            "PKCS12 server Partner2 Client2 (revoked) -> PKCS12 client Partner1 Client1 FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                PARTNER2_CLIENT2_KEYSTORE, PARTNER2_CLIENT2_TRUSTSTORE, null),
            getSoftwareKeyStoreWrapper(
                PARTNER1_CLIENT1_KEYSTORE, PARTNER1_CLIENT1_TRUSTSTORE, CRL_PEM),
            Optional.empty(),
            Optional.of("Certificate revoked"),
          },
          {
            "PKCS12 server Partner1 Client1 -> PKCS12 client Partner2 Client2 (revoked) FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                PARTNER1_CLIENT1_KEYSTORE, PARTNER1_CLIENT1_TRUSTSTORE, CRL_PEM),
            getSoftwareKeyStoreWrapper(
                PARTNER2_CLIENT2_KEYSTORE, PARTNER2_CLIENT2_TRUSTSTORE, null),
            Optional.of("Certificate revoked"),
            Optional.of("certificate_unknown")
          }
        });
  }

  @BeforeEach
  void init() {}

  @AfterEach
  void tearDown() {
    if (client != null) {
      client.stop();
    }
    if (server != null) {
      server.stop();
    }
  }

  private static Path toPath(final String path) throws Exception {
    return null == path ? null : Path.of(TLSContextFactoryTest.class.getResource(path).toURI());
  }

  private static KeyStoreWrapper getHardwareKeyStoreWrapper(
      final String config, final String crlLocation) {
    try {
      return new HardwareKeyStoreWrapper(keystorePassword, toPath(config), toPath(crlLocation));
    } catch (final Exception e) {
      // Not a mac, probably a production build. Full failure.
      throw new PkiException("Failed to initialize hardware keystore", e);
    }
  }

  private static KeyStoreWrapper getSoftwareKeyStoreWrapper(
      final String jksKeyStore, final String trustStore, final String crl) {
    try {
      return new SoftwareKeyStoreWrapper(
          KEYSTORE_TYPE_PKCS12,
          toPath(jksKeyStore),
          keystorePassword,
          KEYSTORE_TYPE_PKCS12,
          toPath(trustStore),
          keystorePassword,
          toPath(crl));
    } catch (final Exception e) {
      throw new PkiException("Failed to initialize software keystore", e);
    }
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("softwareKeysData")
  @DisabledOnOs(OS.MAC)
  void testConnectionSoftwareKeys(
      final String ignoredTestDescription,
      final boolean testSuccess,
      final KeyStoreWrapper serverKeyStoreWrapper,
      final KeyStoreWrapper clientKeyStoreWrapper,
      final Optional<String> serverFailureMessage,
      final Optional<String> clientFailureMessage)
      throws Exception {
    testConnection(
        testSuccess,
        serverKeyStoreWrapper,
        clientKeyStoreWrapper,
        serverFailureMessage,
        clientFailureMessage);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("hardwareKeysData")
  @DisabledOnOs(OS.MAC)
  void testConnectionHardwareKeys(
      final String ignoredTestDescription,
      final boolean testSuccess,
      final KeyStoreWrapper serverKeyStoreWrapper,
      final KeyStoreWrapper clientKeyStoreWrapper,
      final Optional<String> serverFailureMessage,
      final Optional<String> clientFailureMessage)
      throws Exception {
    testConnection(
        testSuccess,
        serverKeyStoreWrapper,
        clientKeyStoreWrapper,
        serverFailureMessage,
        clientFailureMessage);
  }

  private void testConnection(
      final boolean testSuccess,
      final KeyStoreWrapper serverKeyStoreWrapper,
      final KeyStoreWrapper clientKeyStoreWrapper,
      final Optional<String> serverFailureMessage,
      final Optional<String> clientFailureMessage)
      throws Exception {
    final CountDownLatch serverLatch = new CountDownLatch(MAX_NUMBER_MESSAGES);
    final CountDownLatch clientLatch = new CountDownLatch(MAX_NUMBER_MESSAGES);
    server = startServer(serverKeyStoreWrapper, serverLatch);
    client = startClient(server.port, clientKeyStoreWrapper, clientLatch);

    if (testSuccess) {
      client.getChannelFuture().channel().writeAndFlush(Unpooled.copyInt(0));
      final boolean allMessagesServerExchanged = serverLatch.await(10, TimeUnit.SECONDS);
      final boolean allMessagesClientExchanged = clientLatch.await(10, TimeUnit.SECONDS);
      assertThat(allMessagesClientExchanged && allMessagesServerExchanged).isTrue();
    } else {
      try {
        client.getChannelFuture().channel().writeAndFlush(Unpooled.copyInt(0)).sync();
        serverLatch.await(2, TimeUnit.SECONDS);
        assertThat(client.getChannelFuture().channel().isActive()).isFalse();

        if (serverFailureMessage.isPresent()) {
          assertThat(server.getCause()).isNotEmpty();
          assertThat(server.getCause().get()).hasMessageContaining(serverFailureMessage.get());
        } else {
          assertThat(server.getCause()).isEmpty();
        }

        if (clientFailureMessage.isPresent()) {
          assertThat(client.getCause()).isNotEmpty();
          assertThat(client.getCause().get()).hasMessageContaining(clientFailureMessage.get());
        } else {
          assertThat(client.getCause()).isEmpty();
        }
      } catch (final Exception e) {
        // NOOP
      }
    }
  }

  private Server startServer(final KeyStoreWrapper keyStoreWrapper, final CountDownLatch latch)
      throws Exception {

    final Server nettyServer = new Server(keystorePassword, keyStoreWrapper, latch);
    nettyServer.start();
    return nettyServer;
  }

  private Client startClient(
      final int port, final KeyStoreWrapper keyStoreWrapper, final CountDownLatch latch)
      throws Exception {

    final Client nettyClient = new Client(port, keystorePassword, keyStoreWrapper, latch);
    nettyClient.start();
    return nettyClient;
  }

  static class MessageHandler extends ChannelInboundHandlerAdapter {
    private final String id;
    private final CountDownLatch latch;

    private Throwable cause;

    MessageHandler(final String id, final CountDownLatch latch) {
      this.id = id;
      this.latch = latch;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg)
        throws InterruptedException {
      final int message = ((ByteBuf) msg).readInt();
      LOG.info("[" + id + "] Received message: " + message);

      if (message < 2 * MAX_NUMBER_MESSAGES) {
        final int replyMessage = message + 1;
        ctx.writeAndFlush(Unpooled.copyInt(replyMessage)).sync();
        LOG.info("[" + id + "] Sent message: " + replyMessage);
        this.latch.countDown();
        LOG.info("Remaining {}", this.latch.getCount());
      }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause)
        throws Exception {
      this.cause = cause;
    }

    public Optional<Throwable> getCause() {
      return Optional.ofNullable(cause);
    }
  }

  static class Client {
    int port;
    private final String keystorePassword;
    private final KeyStoreWrapper keyStoreWrapper;
    private final CountDownLatch latch;

    private ChannelFuture channelFuture;
    private final EventLoopGroup group = new NioEventLoopGroup();
    private MessageHandler messageHandler;

    ChannelFuture getChannelFuture() {
      return channelFuture;
    }

    Client(
        final int port,
        final String keystorePassword,
        final KeyStoreWrapper keyStoreWrapper,
        final CountDownLatch latch) {
      this.port = port;
      this.keystorePassword = keystorePassword;
      this.keyStoreWrapper = keyStoreWrapper;
      this.latch = latch;
    }

    void start() throws Exception {
      final Bootstrap b = new Bootstrap();
      b.group(group);
      b.channel(NioSocketChannel.class);
      b.handler(
          new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(final SocketChannel socketChannel) throws Exception {
              final SslContext sslContext =
                  TLSContextFactory.getInstance(keystorePassword, keyStoreWrapper)
                      .createNettyClientSslContext();

              final SslHandler sslHandler = sslContext.newHandler(socketChannel.alloc());
              socketChannel.pipeline().addFirst("ssl", sslHandler);
              messageHandler = new MessageHandler("Client", latch);
              socketChannel.pipeline().addLast(messageHandler);
            }
          });

      this.channelFuture = b.connect("127.0.0.1", this.port).sync();
    }

    void stop() {
      group.shutdownGracefully();
      messageHandler = null;
    }

    Optional<Throwable> getCause() {
      return messageHandler != null ? messageHandler.getCause() : Optional.empty();
    }
  }

  static class Server {
    private int port;
    private final String keystorePassword;
    private final KeyStoreWrapper keyStoreWrapper;
    private final CountDownLatch latch;

    private Channel channel;

    private final EventLoopGroup parentGroup = new NioEventLoopGroup();
    private final EventLoopGroup childGroup = new NioEventLoopGroup();

    private MessageHandler messageHandler;

    Server(
        final String keystorePassword,
        final KeyStoreWrapper keyStoreWrapper,
        final CountDownLatch latch) {
      this.keystorePassword = keystorePassword;
      this.keyStoreWrapper = keyStoreWrapper;
      this.latch = latch;
    }

    void start() throws Exception {
      final ServerBootstrap sb = new ServerBootstrap();
      sb.group(parentGroup, childGroup)
          .channel(NioServerSocketChannel.class)
          .childHandler(
              new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(final SocketChannel socketChannel) throws Exception {
                  final SslContext sslContext =
                      TLSContextFactory.getInstance(keystorePassword, keyStoreWrapper)
                          .createNettyServerSslContext();
                  final SslHandler sslHandler = sslContext.newHandler(channel.alloc());
                  socketChannel.pipeline().addFirst("ssl", sslHandler);

                  socketChannel
                      .pipeline()
                      .addLast(messageHandler = new MessageHandler("Server", latch));
                }
              });

      final ChannelFuture cf = sb.bind(0).sync();
      this.channel = cf.channel();
      this.port = ((InetSocketAddress) channel.localAddress()).getPort();
    }

    void stop() {
      childGroup.shutdownGracefully();
      parentGroup.shutdownGracefully();
    }

    Optional<Throwable> getCause() {
      return messageHandler != null ? messageHandler.getCause() : Optional.empty();
    }
  }
}
