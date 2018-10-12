package net.consensys.pantheon.ethereum.p2p.netty;

import net.consensys.pantheon.ethereum.p2p.api.PeerConnection;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import net.consensys.pantheon.ethereum.p2p.wire.messages.PingMessage;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class WireKeepAlive extends ChannelDuplexHandler {
  private static final Logger LOG = LogManager.getLogger();

  private final AtomicBoolean waitingForPong;

  private final PeerConnection connection;

  WireKeepAlive(final PeerConnection connection, final AtomicBoolean waitingForPong) {
    this.connection = connection;
    this.waitingForPong = waitingForPong;
  }

  @Override
  public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt)
      throws IOException {
    if (!(evt instanceof IdleStateEvent
        && ((IdleStateEvent) evt).state() == IdleState.READER_IDLE)) {
      // We only care about idling of incoming data from our peer
      return;
    }

    if (waitingForPong.get()) {
      // We are still waiting for a response from our last pong, disconnect with timeout error
      LOG.info("Wire PONG never received, disconnecting from peer.");
      connection.disconnect(DisconnectReason.TIMEOUT);
      return;
    }

    LOG.debug("Idle connection detected, sending Wire PING to peer.");
    connection.send(null, PingMessage.get());
    waitingForPong.set(true);
  }
}
