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

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.RawMessage;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.google.common.io.Resources;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/** Tests for {@link BlockHeadersMessage}. */
public final class BlockHeadersMessageTest {

  @Test
  public void blockHeadersRoundTrip() throws IOException {
    final List<BlockHeader> headers = new ArrayList<>();
    final ByteBuffer buffer =
        ByteBuffer.wrap(Resources.toByteArray(this.getClass().getResource("/50.blocks")));
    for (int i = 0; i < 50; ++i) {
      final int blockSize = RLP.calculateSize(BytesValue.wrapBuffer(buffer));
      final byte[] block = new byte[blockSize];
      buffer.get(block);
      buffer.compact().position(0);
      final RLPInput oneBlock = new BytesValueRLPInput(BytesValue.wrap(block), false);
      oneBlock.enterList();
      headers.add(BlockHeader.readFrom(oneBlock, new MainnetBlockHeaderFunctions()));
      // We don't care about the bodies, just the headers
      oneBlock.skipNext();
      oneBlock.skipNext();
    }
    final MessageData initialMessage = BlockHeadersMessage.create(headers);
    final MessageData raw = new RawMessage(EthPV62.BLOCK_HEADERS, initialMessage.getData());
    final BlockHeadersMessage message = BlockHeadersMessage.readFrom(raw);
    final List<BlockHeader> readHeaders =
        message.getHeaders(
            FixedDifficultyProtocolSchedule.create(
                GenesisConfigFile.development().getConfigOptions(), false));

    for (int i = 0; i < 50; ++i) {
      Assertions.assertThat(readHeaders.get(i)).isEqualTo(headers.get(i));
    }
  }
}
