package tech.pegasys.pantheon.ethereum.p2p.wire.messages;

import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;

import io.netty.buffer.ByteBuf;

/** A message without a body. */
abstract class EmptyMessage implements MessageData {

  @Override
  public final int getSize() {
    return 0;
  }

  @Override
  public final void writeTo(final ByteBuf output) {}

  @Override
  public final void release() {}

  @Override
  public final void retain() {}
}
