package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.core.Hash;
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

public final class GetReceiptsMessage extends AbstractMessageData {

  public static GetReceiptsMessage readFrom(final MessageData message) {
    if (message instanceof GetReceiptsMessage) {
      message.retain();
      return (GetReceiptsMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV63.GET_RECEIPTS) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a GetReceipts.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new GetReceiptsMessage(data);
  }

  public static GetReceiptsMessage create(final Iterable<Hash> hashes) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    hashes.forEach(tmp::writeBytesValue);
    tmp.endList();
    final ByteBuf data = NetworkMemoryPool.allocate(tmp.encodedSize());
    data.writeBytes(tmp.encoded().extractArray());
    return new GetReceiptsMessage(data);
  }

  private GetReceiptsMessage(final ByteBuf data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV63.GET_RECEIPTS;
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
