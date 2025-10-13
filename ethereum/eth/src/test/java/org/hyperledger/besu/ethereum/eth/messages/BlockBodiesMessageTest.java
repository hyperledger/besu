/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.messages;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link BlockBodiesMessage}. */
public final class BlockBodiesMessageTest {

  private ProtocolSchedule protocolSchedule;

  @BeforeEach
  public void setup() {
    protocolSchedule =
        FixedDifficultyProtocolSchedule.create(
            GenesisConfig.fromResource("/dev.json").getConfigOptions(),
            false,
            EvmConfiguration.DEFAULT,
            MiningConfiguration.MINING_DISABLED,
            new BadBlockManager(),
            false,
            new NoOpMetricsSystem());
  }

  @Test
  public void blockBodiesRoundTrip() throws IOException {
    final List<BlockBody> bodies = new ArrayList<>();
    final ByteBuffer buffer =
        ByteBuffer.wrap(Resources.toByteArray(this.getClass().getResource("/50.blocks")));
    for (int i = 0; i < 50; ++i) {
      final int blockSize = RLP.calculateSize(Bytes.wrapByteBuffer(buffer));
      final byte[] block = new byte[blockSize];
      buffer.get(block);
      buffer.compact().position(0);
      final RLPInput oneBlock = new BytesValueRLPInput(Bytes.wrap(block), false);
      oneBlock.enterList();
      // We don't care about the header, just the body
      oneBlock.skipNext();
      bodies.add(
          // We know the test data to only contain Frontier blocks
          new BlockBody(
              oneBlock.readList(Transaction::readFrom),
              oneBlock.readList(
                  rlp -> BlockHeader.readFrom(rlp, new MainnetBlockHeaderFunctions()))));
    }
    final MessageData initialMessage = BlockBodiesMessage.create(bodies);
    final MessageData raw = new RawMessage(EthPV62.BLOCK_BODIES, initialMessage.getData());
    final BlockBodiesMessage message = BlockBodiesMessage.readFrom(raw);
    final Iterator<BlockBody> readBodies = message.bodies(protocolSchedule).iterator();
    for (int i = 0; i < 50; ++i) {
      Assertions.assertThat(readBodies.next()).isEqualTo(bodies.get(i));
    }
  }

  @Test
  public void shouldEncodeEmptyBlocksInBlockBodiesMessage() {
    final Bytes bytes = Bytes.fromHexString("0xc2c0c0");
    final MessageData raw = new RawMessage(EthPV62.BLOCK_BODIES, bytes);
    final BlockBodiesMessage message = BlockBodiesMessage.readFrom(raw);
    final List<BlockBody> bodies = message.bodies(protocolSchedule);
    bodies.forEach(blockBody -> Assertions.assertThat(blockBody.isEmpty()).isTrue());
  }

  @Test
  public void shouldNotThrowRLPExceptionIfAllowedEmptyBody() {
    final Bytes bytes = Bytes.fromHexString("0xc0");
    final BlockBody empty = BlockBody.readWrappedBodyFrom(RLP.input(bytes), null, true);
    Assertions.assertThat(empty.isEmpty()).isTrue();
  }

  @Test
  public void shouldThrowRLPExceptionIfNotAllowedEmptyBody() {
    final Bytes bytes = Bytes.fromHexString("0xc0");
    final BlockHeaderFunctions blockHeaderFunctions =
        ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);
    assertThrows(
        RLPException.class,
        () -> {
          BlockBody.readWrappedBodyFrom(RLP.input(bytes), blockHeaderFunctions, false);
        });
  }
}
