package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHashFunction;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import net.consensys.pantheon.ethereum.p2p.NetworkMemoryPool;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPInput;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import net.consensys.pantheon.util.bytes.BytesValue;

import io.netty.buffer.ByteBuf;

public final class BlockBodiesMessage extends AbstractMessageData {

  public static BlockBodiesMessage readFrom(final MessageData message) {
    if (message instanceof BlockBodiesMessage) {
      message.retain();
      return (BlockBodiesMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV62.BLOCK_BODIES) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a BlockBodiesMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new BlockBodiesMessage(data);
  }

  public static BlockBodiesMessage create(final Iterable<BlockBody> bodies) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    bodies.forEach(body -> body.writeTo(tmp));
    tmp.endList();
    final ByteBuf data = NetworkMemoryPool.allocate(tmp.encodedSize());
    data.writeBytes(tmp.encoded().extractArray());
    return new BlockBodiesMessage(data);
  }

  private BlockBodiesMessage(final ByteBuf data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV62.BLOCK_BODIES;
  }

  public <C> Iterable<BlockBody> bodies(final ProtocolSchedule<C> protocolSchedule) {
    final BlockHashFunction blockHashFunction =
        ScheduleBasedBlockHashFunction.create(protocolSchedule);
    final byte[] tmp = new byte[data.readableBytes()];
    data.getBytes(0, tmp);
    return new BytesValueRLPInput(BytesValue.wrap(tmp), false)
        .readList(rlp -> BlockBody.readFrom(rlp, blockHashFunction));
  }
}
