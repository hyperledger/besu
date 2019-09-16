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

import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;

public final class BlockBodiesMessage extends AbstractMessageData {

  public static BlockBodiesMessage readFrom(final MessageData message) {
    if (message instanceof BlockBodiesMessage) {
      return (BlockBodiesMessage) message;
    }
    final int code = message.getCode();
    if (code != EthPV62.BLOCK_BODIES) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a BlockBodiesMessage.", code));
    }
    return new BlockBodiesMessage(message.getData());
  }

  public static BlockBodiesMessage create(final Iterable<BlockBody> bodies) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    bodies.forEach(body -> body.writeTo(tmp));
    tmp.endList();
    return new BlockBodiesMessage(tmp.encoded());
  }

  private BlockBodiesMessage(final BytesValue data) {
    super(data);
  }

  @Override
  public int getCode() {
    return EthPV62.BLOCK_BODIES;
  }

  public <C> List<BlockBody> bodies(final ProtocolSchedule<C> protocolSchedule) {
    final BlockHeaderFunctions blockHeaderFunctions =
        ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);
    return new BytesValueRLPInput(data, false)
        .readList(rlp -> BlockBody.readFrom(rlp, blockHeaderFunctions));
  }
}
