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

import org.hyperledger.besu.pki.PkiException;
import org.hyperledger.besu.pki.keystore.HardwareKeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.SoftwareKeyStoreWrapper;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
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
import org.junit.Assume;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TLSContextFactoryTest {

  private static final String JKS = "JKS";
  private static final String validKeystorePassword = "test123";
  private static final String partner1client1PKCS11Config = "/keys/partner1client1/nss.cfg";
  private static final String partner1client1JKSKeystore = "/keys/partner1client1/keystore.jks";
  private static final String partner1client1JKSTruststore = "/keys/partner1client1/truststore.jks";
  private static final String partner1client1CRL = "/keys/partner1client1/crl.pem";
  private static final String partner1client2rvkJKSKeystore =
      "/keys/partner1client2rvk/keystore.jks";
  private static final String partner1client2rvkJKSTruststore =
      "/keys/partner1client2rvk/truststore.jks";
  private static final String partner2client1JKSKeystore = "/keys/partner2client1/keystore.jks";
  private static final String partner2client1JKSTruststore = "/keys/partner2client1/truststore.jks";
  private static final String partner2client1CRL = "/keys/partner2client1/crl.pem";
  private static final String invalidPartner1client1JKSKeystore =
      "/keys/invalidpartner1client1/keystore.jks";
  private static final String invalidPartner1client1JKSTruststore =
      "/keys/invalidpartner1client1/truststore.jks";
  private static final String invalidPartner1client1CRL = "/keys/invalidpartner1client1/crl.pem";
  private static final String partner2client2rvkJKSKeystore =
      "/keys/partner2client2rvk/keystore.jks";
  private static final String partner2client2rvkJKSTruststore =
      "/keys/partner2client2rvk/truststore.jks";

  private static final Logger LOG = LoggerFactory.getLogger(TLSContextFactoryTest.class);

  private static final int MAX_NUMBER_MESSAGES = 10;

  private Server server;
  private Client client;

  static Collection<Object[]> hardwareKeysData() {
    return Arrays.asList(
        new Object[][] {
          {
            "PKCS11 serverPartner1Client1 -> JKS clientPartner2Client1 SuccessfulConnection",
            true,
            getHardwareKeyStoreWrapper(partner1client1PKCS11Config, partner1client1CRL),
            getSoftwareKeyStoreWrapper(
                partner2client1JKSKeystore, partner2client1JKSTruststore, partner2client1CRL)
          },
          {
            "PKCS11 serverPartner1Client1 -> JKS clientInvalidPartner1Client1 FailedConnection",
            false,
            getHardwareKeyStoreWrapper(partner1client1PKCS11Config, partner1client1CRL),
            getSoftwareKeyStoreWrapper(
                invalidPartner1client1JKSKeystore,
                invalidPartner1client1JKSTruststore,
                invalidPartner1client1CRL)
          },
          {
            "PKCS11 serverPartner1Client1 -> JKS clientPartner1Client2rvk FailedConnection",
            false,
            getHardwareKeyStoreWrapper(partner1client1PKCS11Config, partner1client1CRL),
            getSoftwareKeyStoreWrapper(
                partner1client2rvkJKSKeystore, partner1client2rvkJKSTruststore, null)
          },
        });
  }

  static Collection<Object[]> softwareKeysData() {
    return Arrays.asList(
        new Object[][] {
          {
            "JKS serverPartner1Client1 -> JKS clientPartner2Client1 SuccessfulConnection",
            true,
            getSoftwareKeyStoreWrapper(
                partner1client1JKSKeystore, partner1client1JKSTruststore, partner1client1CRL),
            getSoftwareKeyStoreWrapper(
                partner2client1JKSKeystore, partner2client1JKSTruststore, partner2client1CRL)
          },
          {
            "JKS serverPartner2Client1 -> JKS clientPartner1Client1 SuccessfulConnection",
            true,
            getSoftwareKeyStoreWrapper(
                partner2client1JKSKeystore, partner2client1JKSTruststore, partner2client1CRL),
            getSoftwareKeyStoreWrapper(
                partner1client1JKSKeystore, partner1client1JKSTruststore, partner1client1CRL)
          },
          {
            "JKS serverPartner1Client1 -> JKS clientInvalidPartner1Client1 FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                partner1client1JKSKeystore, partner1client1JKSTruststore, partner1client1CRL),
            getSoftwareKeyStoreWrapper(
                invalidPartner1client1JKSKeystore,
                invalidPartner1client1JKSTruststore,
                invalidPartner1client1CRL)
          },
          {
            "JKS serverInvalidPartner1Client1 -> JKS clientPartner1Client1 FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                invalidPartner1client1JKSKeystore,
                invalidPartner1client1JKSTruststore,
                invalidPartner1client1CRL),
            getSoftwareKeyStoreWrapper(
                partner1client1JKSKeystore, partner1client1JKSTruststore, partner1client1CRL)
          },
          {
            "JKS serverPartner1Client2rvk -> JKS clientPartner2Client1 FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                partner1client2rvkJKSKeystore, partner1client2rvkJKSTruststore, null),
            getSoftwareKeyStoreWrapper(
                partner2client1JKSKeystore, partner2client1JKSTruststore, partner2client1CRL)
          },
          {
            "JKS serverPartner2Client1 -> JKS clientPartner1Client2rvk FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                partner2client1JKSKeystore, partner2client1JKSTruststore, partner2client1CRL),
            getSoftwareKeyStoreWrapper(
                partner1client2rvkJKSKeystore, partner1client2rvkJKSTruststore, null)
          },
          {
            "JKS serverPartner2Client2rvk -> JKS clientPartner1Client1 FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                partner2client2rvkJKSKeystore, partner2client2rvkJKSTruststore, null),
            getSoftwareKeyStoreWrapper(
                partner1client1JKSKeystore, partner1client1JKSTruststore, partner1client1CRL)
          },
          {
            "JKS serverPartner1Client1 -> JKS clientPartner2Client2rvk FailedConnection",
            false,
            getSoftwareKeyStoreWrapper(
                partner1client1JKSKeystore, partner1client1JKSTruststore, partner1client1CRL),
            getSoftwareKeyStoreWrapper(
                partner2client2rvkJKSKeystore, partner2client2rvkJKSTruststore, null)
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
      return new HardwareKeyStoreWrapper(
          validKeystorePassword, toPath(config), toPath(crlLocation));
    } catch (final Exception e) {
      if (OS.MAC.isCurrentOs()) {
        // nss3 is difficult to setup on mac correctly, don't let it break unit tests for dev
        // machines.
        Assume.assumeNoException("Failed to initialize hardware keystore", e);
      }
      // Not a mac, probably a production build. Full failure.
      throw new PkiException("Failed to initialize hardware keystore", e);
    }
  }

  private static KeyStoreWrapper getSoftwareKeyStoreWrapper(
      final String jksKeyStore, final String trustStore, final String crl) {
    try {
      return new SoftwareKeyStoreWrapper(
          JKS,
          toPath(jksKeyStore),
          validKeystorePassword,
          JKS,
          toPath(trustStore),
          null,
          toPath(crl));
    } catch (final Exception e) {
      throw new PkiException("Failed to initialize software keystore", e);
    }
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("softwareKeysData")
  void testConnectionSoftwareKeys(
      final String ignoredTestDescription,
      final boolean testSuccess,
      final KeyStoreWrapper serverKeyStoreWrapper,
      final KeyStoreWrapper clientKeyStoreWrapper)
      throws Exception {
    testConnection(testSuccess, serverKeyStoreWrapper, clientKeyStoreWrapper);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("hardwareKeysData")
  void testConnectionHardwareKeys(
      final String ignoredTestDescription,
      final boolean testSuccess,
      final KeyStoreWrapper serverKeyStoreWrapper,
      final KeyStoreWrapper clientKeyStoreWrapper)
      throws Exception {
    testConnection(testSuccess, serverKeyStoreWrapper, clientKeyStoreWrapper);
  }

  private void testConnection(
      final boolean testSuccess,
      final KeyStoreWrapper serverKeyStoreWrapper,
      final KeyStoreWrapper clientKeyStoreWrapper)
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
      } catch (final Exception e) {
        // NOOP
      }
    }
  }

  private Server startServer(final KeyStoreWrapper keyStoreWrapper, final CountDownLatch latch)
      throws Exception {

    final Server nettyServer = new Server(validKeystorePassword, keyStoreWrapper, latch);
    nettyServer.start();
    return nettyServer;
  }

  private Client startClient(
      final int port, final KeyStoreWrapper keyStoreWrapper, final CountDownLatch latch)
      throws Exception {

    final Client nettyClient = new Client(port, validKeystorePassword, keyStoreWrapper, latch);
    nettyClient.start();
    return nettyClient;
  }

  static class MessageHandler extends ChannelInboundHandlerAdapter {
    private final String id;
    private final CountDownLatch latch;

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
  }

  static class Client {
    int port;
    private final String keystorePassword;
    private final KeyStoreWrapper keyStoreWrapper;
    private final CountDownLatch latch;

    private ChannelFuture channelFuture;
    private final EventLoopGroup group = new NioEventLoopGroup();

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

              socketChannel.pipeline().addLast(new MessageHandler("Client", latch));
            }
          });

      this.channelFuture = b.connect("127.0.0.1", this.port).sync();
    }

    void stop() {
      group.shutdownGracefully();
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

                  socketChannel.pipeline().addLast(new MessageHandler("Server", latch));
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
  }
}
