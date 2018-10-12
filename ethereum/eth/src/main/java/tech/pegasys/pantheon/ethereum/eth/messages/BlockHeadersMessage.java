package tech.pegasys.pantheon.ethereum.eth.messages;

import tech.pegasys.pantheon.ethereum.core.BlockHashFunction;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Iterator;

import io.netty.buffer.ByteBuf;

public final class BlockHeadersMessage extends AbstractMessageData {

  public static BlockHeadersMessage readFrom(final MessageData message) {
    if (message instanceof BlockHeadersMessage) {
      message.retain();
      return (BlockHeadersMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV62.BLOCK_HEADERS) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a BlockHeadersMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new BlockHeadersMessage(data);
  }

  public static BlockHeadersMessage create(final Iterable<BlockHeader> headers) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    for (final BlockHeader header : headers) {
      header.writeTo(tmp);
    }
    tmp.endList();
    final ByteBuf data = NetworkMemoryPool.allocate(tmp.encodedSize());
    data.writeBytes(tmp.encoded().extractArray());
    return new BlockHeadersMessage(data);
  }

  private BlockHeadersMessage(final ByteBuf data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV62.BLOCK_HEADERS;
  }

  public <C> Iterator<BlockHeader> getHeaders(final ProtocolSchedule<C> protocolSchedule) {
    final BlockHashFunction blockHashFunction =
        ScheduleBasedBlockHashFunction.create(protocolSchedule);
    final byte[] headers = new byte[data.readableBytes()];
    data.getBytes(0, headers);
    return new BytesValueRLPInput(BytesValue.wrap(headers), false)
        .readList(rlp -> BlockHeader.readFrom(rlp, blockHashFunction))
        .iterator();
  }
}
