package tech.pegasys.pantheon.ethereum.p2p.netty;

import static java.util.Collections.emptyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.HelloMessage;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class NettyPeerConnectionTest {

  private final ChannelHandlerContext context = mock(ChannelHandlerContext.class);
  private final Channel channel = mock(Channel.class);
  private final ChannelFuture closeFuture = mock(ChannelFuture.class);
  private final EventLoop eventLoop = mock(EventLoop.class);
  private final CapabilityMultiplexer multiplexer = mock(CapabilityMultiplexer.class);
  private final Callbacks callbacks = mock(Callbacks.class);
  private final PeerInfo peerInfo = new PeerInfo(5, "foo", emptyList(), 0, BytesValue.of(1));

  private NettyPeerConnection connection;

  @Before
  public void setUp() {
    when(context.channel()).thenReturn(channel);
    when(channel.closeFuture()).thenReturn(closeFuture);
    when(channel.eventLoop()).thenReturn(eventLoop);
    connection = new NettyPeerConnection(context, peerInfo, multiplexer, callbacks);
  }

  @Test
  public void shouldThrowExceptionWhenAttemptingToSendMessageOnClosedConnection() {
    connection.disconnect(DisconnectReason.SUBPROTOCOL_TRIGGERED);
    Assertions.assertThatThrownBy(() -> connection.send(null, HelloMessage.create(peerInfo)))
        .isInstanceOfAny(PeerNotConnected.class);
  }
}
