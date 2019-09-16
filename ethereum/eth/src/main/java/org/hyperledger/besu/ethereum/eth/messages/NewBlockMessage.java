/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.eth.messages;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

public class NewBlockMessage extends AbstractMessageData {

  private static final int MESSAGE_CODE = EthPV62.NEW_BLOCK;

  private NewBlockMessageData messageFields = null;

  private NewBlockMessage(final BytesValue data) {
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
    return new NewBlockMessage(out.encoded());
  }

  public static NewBlockMessage readFrom(final MessageData message) {
    if (message instanceof NewBlockMessage) {
      return (NewBlockMessage) message;
    }
    final int code = message.getCode();
    if (code != NewBlockMessage.MESSAGE_CODE) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a NewBlockMessage.", code));
    }
    return new NewBlockMessage(message.getData());
  }

  public <C> Block block(final ProtocolSchedule<C> protocolSchedule) {
    return messageFields(protocolSchedule).block();
  }

  public <C> UInt256 totalDifficulty(final ProtocolSchedule<C> protocolSchedule) {
    return messageFields(protocolSchedule).totalDifficulty();
  }

  private <C> NewBlockMessageData messageFields(final ProtocolSchedule<C> protocolSchedule) {
    if (messageFields == null) {
      final RLPInput input = RLP.input(data);
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
      final BlockHeaderFunctions blockHeaderFunctions =
          ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);
      in.enterList();
      final Block block = Block.readFrom(in, blockHeaderFunctions);
      final UInt256 totaldifficulty = in.readUInt256Scalar();
      return new NewBlockMessageData(block, totaldifficulty);
    }
  }
}
