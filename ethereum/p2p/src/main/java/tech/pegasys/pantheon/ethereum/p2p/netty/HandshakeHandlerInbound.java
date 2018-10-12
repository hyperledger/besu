package tech.pegasys.pantheon.ethereum.p2p.netty;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.handshake.Handshaker;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.SubProtocol;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.netty.buffer.ByteBuf;

public final class HandshakeHandlerInbound extends AbstractHandshakeHandler {

  public HandshakeHandlerInbound(
      final SECP256K1.KeyPair kp,
      final List<SubProtocol> subProtocols,
      final PeerInfo ourInfo,
      final CompletableFuture<PeerConnection> connectionFuture,
      final Callbacks callbacks,
      final PeerConnectionRegistry peerConnectionRegistry) {
    super(subProtocols, ourInfo, connectionFuture, callbacks, peerConnectionRegistry);
    handshaker.prepareResponder(kp);
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
}
