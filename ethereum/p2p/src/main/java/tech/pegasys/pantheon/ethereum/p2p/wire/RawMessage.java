package tech.pegasys.pantheon.ethereum.p2p.wire;

import io.netty.buffer.ByteBuf;

public final class RawMessage extends AbstractMessageData {

  private final int code;

  public RawMessage(final int code, final ByteBuf data) {
    super(data);
    this.code = code;
  }

  @Override
  public int getCode() {
    return code;
  }
}
