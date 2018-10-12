package tech.pegasys.pantheon.ethereum.eth.messages;

import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

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
