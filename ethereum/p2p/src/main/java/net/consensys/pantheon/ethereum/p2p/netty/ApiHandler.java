package net.consensys.pantheon.ethereum.p2p.netty;

import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.api.PeerConnection;
import net.consensys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import net.consensys.pantheon.ethereum.p2p.wire.messages.PongMessage;
import net.consensys.pantheon.ethereum.p2p.wire.messages.WireMessageCodes;
import net.consensys.pantheon.ethereum.rlp.RLPException;

import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class ApiHandler extends SimpleChannelInboundHandler<MessageData> {

  private static final Logger LOGGER = LogManager.getLogger(ApiHandler.class);

  private final CapabilityMultiplexer multiplexer;
  private final AtomicBoolean waitingForPong;

  private final Callbacks callbacks;

  private final PeerConnection connection;

  ApiHandler(
      final CapabilityMultiplexer multiplexer,
      final PeerConnection connection,
      final Callbacks callbacks,
      final AtomicBoolean waitingForPong) {
    this.multiplexer = multiplexer;
    this.callbacks = callbacks;
    this.connection = connection;
    this.waitingForPong = waitingForPong;
  }

  @Override
  protected void channelRead0(final ChannelHandlerContext ctx, final MessageData originalMessage) {
    final CapabilityMultiplexer.ProtocolMessage demultiplexed =
        multiplexer.demultiplex(originalMessage);

    final MessageData message = demultiplexed.getMessage();

    // Handle Wire messages
    if (demultiplexed.getCapability() == null) {
      switch (message.getCode()) {
        case WireMessageCodes.PING:
          LOGGER.debug("Received Wire PING");
          try {
            connection.send(null, PongMessage.get());
          } catch (final PeerNotConnected peerNotConnected) {
            // Nothing to do
          }
          break;
        case WireMessageCodes.PONG:
          LOGGER.debug("Received Wire PONG");
          waitingForPong.set(false);
          break;
        case WireMessageCodes.DISCONNECT:
          final DisconnectMessage disconnect = DisconnectMessage.readFrom(message);
          DisconnectReason reason = null;
          try {
            reason = disconnect.getReason();
            LOGGER.info(
                "Received Wire DISCONNECT ({}) from peer: {}",
                reason.name(),
                connection.getPeer().getClientId());
          } catch (final RLPException e) {
            // It seems pretty common to get disconnect messages with no reason, which results in an
            // rlp parsing error
            LOGGER.warn(
                "Received Wire DISCONNECT, but unable to parse reason. Peer: {}",
                connection.getPeer().getClientId());
          } catch (final Exception e) {
            LOGGER.error(
                "Received Wire DISCONNECT, but unable to parse reason. Peer: {}",
                connection.getPeer().getClientId(),
                e);
          }

          connection.terminateConnection(reason, true);
      }
      return;
    }
    callbacks.invokeSubProtocol(connection, demultiplexed.getCapability(), message);
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable throwable) {
    LOGGER.error("Error:", throwable);
    callbacks.invokeDisconnect(connection, DisconnectReason.TCP_SUBSYSTEM_ERROR, false);
    ctx.close();
  }
}
