package net.consensys.pantheon.ethereum.eth.messages;

import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockHashFunction;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import net.consensys.pantheon.ethereum.p2p.NetworkMemoryPool;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.utils.ByteBufUtils;
import net.consensys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import net.consensys.pantheon.ethereum.rlp.RLP;
import net.consensys.pantheon.ethereum.rlp.RLPInput;
import net.consensys.pantheon.ethereum.rlp.RLPOutput;
import net.consensys.pantheon.util.bytes.BytesValue;
import net.consensys.pantheon.util.uint.UInt256;

import io.netty.buffer.ByteBuf;

public class NewBlockMessage extends AbstractMessageData {

  private static final int MESSAGE_CODE = EthPV62.NEW_BLOCK;

  private NewBlockMessageData messageFields = null;

  private NewBlockMessage(final ByteBuf data) {
    super(data);
  }

  @Override
  public int getCode() {
    return MESSAGE_CODE;
  }

  public static NewBlockMessage create(final Block block, final UInt256 totalDifficulty) {
    final NewBlockMessageData msgData = new NewBlockMessageData(block, totalDifficulty);
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    msgData.writeTo(out);
    final ByteBuf data = ByteBufUtils.fromRLPOutput(out);
    return new NewBlockMessage(data);
  }

  public static NewBlockMessage readFrom(final MessageData message) {
    if (message instanceof NewBlockMessage) {
      message.retain();
      return (NewBlockMessage) message;
    }
    final int code = message.getCode();
    if (code != NewBlockMessage.MESSAGE_CODE) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a NewBlockMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new NewBlockMessage(data);
  }

  public <C> Block block(final ProtocolSchedule<C> protocolSchedule) {
    return messageFields(protocolSchedule).block();
  }

  public <C> UInt256 totalDifficulty(final ProtocolSchedule<C> protocolSchedule) {
    return messageFields(protocolSchedule).totalDifficulty();
  }

  private <C> NewBlockMessageData messageFields(final ProtocolSchedule<C> protocolSchedule) {
    if (messageFields == null) {
      final RLPInput input = RLP.input(BytesValue.wrap(ByteBufUtils.toByteArray(data)));
      messageFields = NewBlockMessageData.readFrom(input, protocolSchedule);
    }
    return messageFields;
  }

  public static class NewBlockMessageData {

    private final Block block;
    private final UInt256 totalDifficulty;

    public NewBlockMessageData(final Block block, final UInt256 totalDifficulty) {
      this.block = block;
      this.totalDifficulty = totalDifficulty;
    }

    public Block block() {
      return block;
    }

    public UInt256 totalDifficulty() {
      return totalDifficulty;
    }

    public void writeTo(final RLPOutput out) {
      out.startList();
      block.writeTo(out);
      out.writeUInt256Scalar(totalDifficulty);
      out.endList();
    }

    public static <C> NewBlockMessageData readFrom(
        final RLPInput in, final ProtocolSchedule<C> protocolSchedule) {
      final BlockHashFunction blockHashFunction =
          ScheduleBasedBlockHashFunction.create(protocolSchedule);
      in.enterList();
      final Block block = Block.readFrom(in, blockHashFunction);
      final UInt256 totaldifficulty = in.readUInt256Scalar();
      return new NewBlockMessageData(block, totaldifficulty);
    }
  }
}
