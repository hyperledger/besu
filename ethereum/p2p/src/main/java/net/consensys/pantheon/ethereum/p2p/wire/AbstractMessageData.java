package net.consensys.pantheon.ethereum.p2p.wire;

import net.consensys.pantheon.ethereum.p2p.api.MessageData;

import io.netty.buffer.ByteBuf;

public abstract class AbstractMessageData implements MessageData {

  protected final ByteBuf data;

  protected AbstractMessageData(final ByteBuf data) {
    this.data = data;
  }

  @Override
  public final int getSize() {
    return data.readableBytes();
  }

  @Override
  public final void writeTo(final ByteBuf output) {
    data.markReaderIndex();
    output.writeBytes(data);
    data.resetReaderIndex();
  }

  @Override
  public final void release() {
    data.release();
  }

  @Override
  public final void retain() {
    data.retain();
  }
}
