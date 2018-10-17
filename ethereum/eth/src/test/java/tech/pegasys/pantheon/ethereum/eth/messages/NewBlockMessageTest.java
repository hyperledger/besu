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
package tech.pegasys.pantheon.ethereum.eth.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.wire.RawMessage;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.testutil.BlockDataGenerator;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import io.netty.buffer.Unpooled;
import org.junit.Test;

public class NewBlockMessageTest {
  private static final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();

  @Test
  public void roundTripNewBlockMessage() {
    final UInt256 totalDifficulty = UInt256.of(98765);
    final BlockDataGenerator blockGenerator = new BlockDataGenerator();
    final Block blockForInsertion = blockGenerator.block();

    final NewBlockMessage msg = NewBlockMessage.create(blockForInsertion, totalDifficulty);
    assertThat(msg.getCode()).isEqualTo(EthPV62.NEW_BLOCK);
    assertThat(msg.totalDifficulty(protocolSchedule)).isEqualTo(totalDifficulty);
    final Block extractedBlock = msg.block(protocolSchedule);
    assertThat(extractedBlock).isEqualTo(blockForInsertion);
  }

  @Test
  public void rawMessageUpCastsToANewBlockMessage() {
    final UInt256 totalDifficulty = UInt256.of(12345);
    final BlockDataGenerator blockGenerator = new BlockDataGenerator();
    final Block blockForInsertion = blockGenerator.block();

    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    blockForInsertion.writeTo(tmp);
    tmp.writeUInt256Scalar(totalDifficulty);
    tmp.endList();

    final BytesValue msgPayload = tmp.encoded();

    final RawMessage rawMsg =
        new RawMessage(EthPV62.NEW_BLOCK, Unpooled.wrappedBuffer(tmp.encoded().extractArray()));

    final NewBlockMessage newBlockMsg = NewBlockMessage.readFrom(rawMsg);

    assertThat(newBlockMsg.getCode()).isEqualTo(EthPV62.NEW_BLOCK);
    assertThat(newBlockMsg.totalDifficulty(protocolSchedule)).isEqualTo(totalDifficulty);
    final Block extractedBlock = newBlockMsg.block(protocolSchedule);
    assertThat(extractedBlock).isEqualTo(blockForInsertion);
  }

  @Test
  public void readFromMessageWithWrongCodeThrows() {
    final ProtocolSchedule<Void> protSchedule = MainnetProtocolSchedule.create();
    final RawMessage rawMsg = new RawMessage(EthPV62.BLOCK_HEADERS, NetworkMemoryPool.allocate(1));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> NewBlockMessage.readFrom(rawMsg));
  }
}
