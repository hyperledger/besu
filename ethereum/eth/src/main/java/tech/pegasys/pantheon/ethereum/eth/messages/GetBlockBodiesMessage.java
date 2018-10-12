package tech.pegasys.pantheon.ethereum.eth.messages;

import tech.pegasys.pantheon.ethereum.core.Hash;
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

public final class GetBlockBodiesMessage extends AbstractMessageData {

  public static GetBlockBodiesMessage readFrom(final MessageData message) {
    if (message instanceof GetBlockBodiesMessage) {
      message.retain();
      return (GetBlockBodiesMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV62.GET_BLOCK_BODIES) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a GetBlockBodiesMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new GetBlockBodiesMessage(data);
  }

  public static GetBlockBodiesMessage create(final Iterable<Hash> hashes) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    hashes.forEach(tmp::writeBytesValue);
    tmp.endList();
    final ByteBuf data = NetworkMemoryPool.allocate(tmp.encodedSize());
    data.writeBytes(tmp.encoded().extractArray());
    return new GetBlockBodiesMessage(data);
  }

  private GetBlockBodiesMessage(final ByteBuf data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV62.GET_BLOCK_BODIES;
  }

  public Iterable<Hash> hashes() {
    final byte[] tmp = new byte[data.readableBytes()];
    data.getBytes(0, tmp);
    final RLPInput input = new BytesValueRLPInput(BytesValue.wrap(tmp), false);
    input.enterList();
    final Collection<Hash> hashes = new ArrayList<>();
    while (!input.isEndOfCurrentList()) {
      hashes.add(Hash.wrap(input.readBytes32()));
    }
    input.leaveList();
    return hashes;
  }
}
