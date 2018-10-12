package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.p2p.NetworkMemoryPool;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPInput;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import net.consensys.pantheon.ethereum.rlp.RLPInput;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.Collection;

import io.netty.buffer.ByteBuf;

public final class NodeDataMessage extends AbstractMessageData {

  public static NodeDataMessage readFrom(final MessageData message) {
    if (message instanceof NodeDataMessage) {
      message.retain();
      return (NodeDataMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV63.NODE_DATA) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a NodeDataMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new NodeDataMessage(data);
  }

  public static NodeDataMessage create(final Iterable<BytesValue> nodeData) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    nodeData.forEach(tmp::writeBytesValue);
    tmp.endList();
    final ByteBuf data = NetworkMemoryPool.allocate(tmp.encodedSize());
    data.writeBytes(tmp.encoded().extractArray());
    return new NodeDataMessage(data);
  }

  private NodeDataMessage(final ByteBuf data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV63.NODE_DATA;
  }

  public Iterable<BytesValue> nodeData() {
    final byte[] tmp = new byte[data.readableBytes()];
    data.getBytes(0, tmp);
    final RLPInput input = new BytesValueRLPInput(BytesValue.wrap(tmp), false);
    input.enterList();
    final Collection<BytesValue> nodeData = new ArrayList<>();
    while (!input.isEndOfCurrentList()) {
      nodeData.add(input.readBytesValue());
    }
    input.leaveList();
    return nodeData;
  }
}
