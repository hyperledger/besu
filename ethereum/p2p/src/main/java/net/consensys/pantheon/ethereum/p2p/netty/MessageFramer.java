package net.consensys.pantheon.ethereum.p2p.netty;

import net.consensys.pantheon.ethereum.p2p.rlpx.framing.Framer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

final class MessageFramer extends MessageToByteEncoder<OutboundMessage> {

  private final CapabilityMultiplexer multiplexer;

  private final Framer framer;

  MessageFramer(final CapabilityMultiplexer multiplexer, final Framer framer) {
    this.multiplexer = multiplexer;
    this.framer = framer;
  }

  @Override
  protected void encode(
      final ChannelHandlerContext ctx, final OutboundMessage msg, final ByteBuf out) {
    out.writeBytes(framer.frame(multiplexer.multiplex(msg.getCapability(), msg.getData())));
  }
}
