/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.p2p.netty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.netty.exceptions.BreachOfProtocolException;
import tech.pegasys.pantheon.ethereum.p2p.netty.exceptions.IncompatiblePeerException;
import tech.pegasys.pantheon.ethereum.p2p.netty.exceptions.PeerDisconnectedException;
import tech.pegasys.pantheon.ethereum.p2p.netty.testhelpers.NettyMocks;
import tech.pegasys.pantheon.ethereum.p2p.netty.testhelpers.SubProtocolMock;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.framing.Framer;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.framing.FramingException;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.RawMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.HelloMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.PingMessage;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.WireMessageCodes;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.DecoderException;
import io.netty.util.concurrent.ScheduledFuture;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class DeFramerTest {

  private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
  private final Channel channel = mock(Channel.class);
  private final ChannelPipeline pipeline = mock(ChannelPipeline.class);
  private final EventLoop eventLoop = mock(EventLoop.class);
  private final Framer framer = mock(Framer.class);
  private final Callbacks callbacks = mock(Callbacks.class);
  private final PeerConnection peerConnection = mock(PeerConnection.class);
  private final CompletableFuture<PeerConnection> connectFuture = new CompletableFuture<>();
  private final PeerInfo peerInfo =
      new PeerInfo(
          5,
          "abc",
          Arrays.asList(Capability.create("eth", 63)),
          0,
          BytesValue.fromHexString("0x01"));
  private final DeFramer deFramer =
      new DeFramer(
          framer,
          Arrays.asList(SubProtocolMock.create("eth")),
          peerInfo,
          callbacks,
          connectFuture,
          NoOpMetricsSystem.NO_OP_LABELLED_3_COUNTER);

  @Before
  @SuppressWarnings("unchecked")
  public void setup() {
    when(ctx.channel()).thenReturn(channel);

    when(channel.pipeline()).thenReturn(pipeline);
    when(pipeline.addLast(any())).thenReturn(pipeline);
    when(pipeline.addFirst(any())).thenReturn(pipeline);

    when(channel.eventLoop()).thenReturn(eventLoop);
    when(eventLoop.schedule(any(Callable.class), anyLong(), any()))
        .thenReturn(mock(ScheduledFuture.class));
  }

  @Test
  public void exceptionCaught_shouldDisconnectForBreachOfProtocolWhenFramingExceptionThrown()
      throws Exception {
    connectFuture.complete(peerConnection);

    deFramer.exceptionCaught(ctx, new DecoderException(new FramingException("Test")));

    verify(peerConnection).disconnect(DisconnectReason.BREACH_OF_PROTOCOL);
  }

  @Test
  public void exceptionCaught_shouldHandleFramingExceptionWhenFutureCompletedExceptionally()
      throws Exception {
    connectFuture.completeExceptionally(new Exception());

    deFramer.exceptionCaught(ctx, new DecoderException(new FramingException("Test")));

    verify(ctx).close();
  }

  @Test
  public void exceptionCaught_shouldHandleGenericExceptionWhenFutureCompletedExceptionally()
      throws Exception {
    connectFuture.completeExceptionally(new Exception());

    deFramer.exceptionCaught(ctx, new DecoderException(new RuntimeException("Test")));

    verify(ctx).close();
  }

  @Test
  public void decode_handlesHello() throws ExecutionException, InterruptedException {
    ChannelFuture future = NettyMocks.channelFuture(false);
    when(channel.closeFuture()).thenReturn(future);

    PeerInfo remotePeerInfo =
        new PeerInfo(
            peerInfo.getVersion(),
            peerInfo.getClientId(),
            peerInfo.getCapabilities(),
            peerInfo.getPort(),
            Peer.randomId());
    HelloMessage helloMessage = HelloMessage.create(remotePeerInfo);
    ByteBuf data = Unpooled.wrappedBuffer(helloMessage.getData().extractArray());
    when(framer.deframe(eq(data)))
        .thenReturn(new RawMessage(helloMessage.getCode(), helloMessage.getData()))
        .thenReturn(null);
    List<Object> out = new ArrayList<>();
    deFramer.decode(ctx, data, out);

    assertThat(connectFuture).isDone();
    assertThat(connectFuture).isNotCompletedExceptionally();
    PeerConnection peerConnection = connectFuture.get();
    assertThat(peerConnection.getPeer()).isEqualTo(remotePeerInfo);
    assertThat(out).isEmpty();

    // Next phase of pipeline should be setup
    verify(pipeline, times(1)).addLast(any());

    // Next message should be pushed out
    PingMessage nextMessage = PingMessage.get();
    ByteBuf nextData = Unpooled.wrappedBuffer(nextMessage.getData().extractArray());
    when(framer.deframe(eq(nextData)))
        .thenReturn(new RawMessage(nextMessage.getCode(), nextMessage.getData()))
        .thenReturn(null);
    verify(pipeline, times(1)).addLast(any());
    deFramer.decode(ctx, nextData, out);
    assertThat(out.size()).isEqualTo(1);
  }

  @Test
  public void decode_handlesNoSharedCaps() throws ExecutionException, InterruptedException {
    ChannelFuture future = NettyMocks.channelFuture(false);
    when(channel.closeFuture()).thenReturn(future);

    PeerInfo remotePeerInfo =
        new PeerInfo(
            peerInfo.getVersion(),
            peerInfo.getClientId(),
            Arrays.asList(Capability.create("eth", 254)),
            peerInfo.getPort(),
            Peer.randomId());
    HelloMessage helloMessage = HelloMessage.create(remotePeerInfo);
    ByteBuf data = Unpooled.wrappedBuffer(helloMessage.getData().extractArray());
    when(framer.deframe(eq(data)))
        .thenReturn(new RawMessage(helloMessage.getCode(), helloMessage.getData()))
        .thenReturn(null);
    List<Object> out = new ArrayList<>();
    deFramer.decode(ctx, data, out);

    assertThat(connectFuture).isDone();
    assertThat(connectFuture).isCompletedExceptionally();
    assertThatThrownBy(connectFuture::get).hasCauseInstanceOf(IncompatiblePeerException.class);
    assertThat(out).isEmpty();

    // Next phase of pipeline should be setup
    verify(pipeline, times(1)).addLast(any());
  }

  @Test
  public void decode_shouldHandleImmediateDisconnectMessage() {
    DisconnectMessage disconnectMessage = DisconnectMessage.create(DisconnectReason.TOO_MANY_PEERS);
    ByteBuf disconnectData = Unpooled.wrappedBuffer(disconnectMessage.getData().extractArray());
    when(framer.deframe(eq(disconnectData)))
        .thenReturn(new RawMessage(disconnectMessage.getCode(), disconnectMessage.getData()))
        .thenReturn(null);
    List<Object> out = new ArrayList<>();
    deFramer.decode(ctx, disconnectData, out);

    assertThat(connectFuture).isDone();
    assertThatThrownBy(connectFuture::get)
        .hasCauseInstanceOf(PeerDisconnectedException.class)
        .hasMessageContaining(disconnectMessage.getReason().toString());
    verify(ctx).close();
    assertThat(out).isEmpty();
  }

  @Test
  public void decode_shouldHandleInvalidMessage() {
    MessageData messageData = PingMessage.get();
    ByteBuf data = Unpooled.wrappedBuffer(messageData.getData().extractArray());
    when(framer.deframe(eq(data)))
        .thenReturn(new RawMessage(messageData.getCode(), messageData.getData()))
        .thenReturn(null);
    ChannelFuture future = NettyMocks.channelFuture(true);
    when(ctx.writeAndFlush(any())).thenReturn(future);
    List<Object> out = new ArrayList<>();
    deFramer.decode(ctx, data, out);

    ArgumentCaptor<Object> outboundMessageArgumentCaptor =
        ArgumentCaptor.forClass(OutboundMessage.class);
    verify(ctx, times(1)).writeAndFlush(outboundMessageArgumentCaptor.capture());
    OutboundMessage outboundMessage = (OutboundMessage) outboundMessageArgumentCaptor.getValue();
    assertThat(outboundMessage.getCapability()).isNull();
    MessageData outboundMessageData = outboundMessage.getData();
    assertThat(outboundMessageData.getCode()).isEqualTo(WireMessageCodes.DISCONNECT);
    assertThat(DisconnectMessage.readFrom(outboundMessageData).getReason())
        .isEqualTo(DisconnectReason.BREACH_OF_PROTOCOL);

    assertThat(connectFuture).isDone();
    assertThatThrownBy(connectFuture::get).hasCauseInstanceOf(BreachOfProtocolException.class);
    verify(ctx).close();
    assertThat(out).isEmpty();
  }
}
