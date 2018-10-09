package net.consensys.pantheon.ethereum.p2p.wire.messages;

import net.consensys.pantheon.ethereum.p2p.NetworkMemoryPool;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.utils.ByteBufUtils;
import net.consensys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import net.consensys.pantheon.ethereum.p2p.wire.PeerInfo;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPOutput;

import io.netty.buffer.ByteBuf;

public final class HelloMessage extends AbstractMessageData {

  private HelloMessage(final ByteBuf data) {
    super(data);
  }

  public static HelloMessage create(final PeerInfo peerInfo) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    peerInfo.writeTo(out);
    final ByteBuf buf = ByteBufUtils.fromRLPOutput(out);

    return new HelloMessage(buf);
  }

  public static HelloMessage create(final ByteBuf data) {
    return new HelloMessage(data);
  }

  public static HelloMessage readFrom(final MessageData message) {
    if (message instanceof HelloMessage) {
      message.retain();
      return (HelloMessage) message;
    }
    final int code = message.getCode();
    if (code != WireMessageCodes.HELLO) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a HelloMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new HelloMessage(data);
  }

  @Override
  public int getCode() {
    return WireMessageCodes.HELLO;
  }

  public PeerInfo getPeerInfo() {
    return PeerInfo.readFrom(ByteBufUtils.toRLPInput(data));
  }

  @Override
  public String toString() {
    return "HelloMessage{" + "data=" + data + '}';
  }
}
