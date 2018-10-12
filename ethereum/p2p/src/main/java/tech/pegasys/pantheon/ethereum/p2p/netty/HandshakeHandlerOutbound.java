package tech.pegasys.pantheon.ethereum.p2p.netty;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.handshake.Handshaker;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class HandshakeHandlerOutbound extends AbstractHandshakeHandler {

  private static final Logger LOG = LogManager.getLogger();

  private final ByteBuf first;

  public HandshakeHandlerOutbound(
      final SECP256K1.KeyPair kp,
      final BytesValue peerId,
      final List<SubProtocol> subProtocols,
      final PeerInfo ourInfo,
      final CompletableFuture<PeerConnection> connectionFuture,
      final Callbacks callbacks,
      final PeerConnectionRegistry peerConnectionRegistry) {
    super(subProtocols, ourInfo, connectionFuture, callbacks, peerConnectionRegistry);
    handshaker.prepareInitiator(kp, SECP256K1.PublicKey.create(peerId));
    this.first = handshaker.firstMessage();
  }

  @Override
  protected Optional<ByteBuf> nextHandshakeMessage(final ByteBuf msg) {
    final Optional<ByteBuf> nextMsg;
    if (handshaker.getStatus() == Handshaker.HandshakeStatus.IN_PROGRESS) {
      nextMsg = handshaker.handleMessage(msg);
    } else {
      nextMsg = Optional.empty();
    }
    return nextMsg;
  }

  @Override
  public void channelActive(final ChannelHandlerContext ctx) throws Exception {
    super.channelActive(ctx);
    ctx.writeAndFlush(first)
        .addListener(
            f -> {
              if (f.isSuccess()) {
                LOG.debug(
                    "Wrote initial crypto handshake message to {}.", ctx.channel().remoteAddress());
              }
            });
  }
}
