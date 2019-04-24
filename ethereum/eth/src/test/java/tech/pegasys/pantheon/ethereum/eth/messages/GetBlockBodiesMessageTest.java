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

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.RawMessage;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.io.Resources;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/** Tests for {@link GetBlockBodiesMessage}. */
public final class GetBlockBodiesMessageTest {

  @Test
  public void getBlockBodiesRoundTrip() throws IOException {
    final List<Hash> hashes = new ArrayList<>();
    final ByteBuffer buffer =
        ByteBuffer.wrap(Resources.toByteArray(this.getClass().getResource("/50.blocks")));
    for (int i = 0; i < 50; ++i) {
      final int blockSize = RLP.calculateSize(BytesValue.wrapBuffer(buffer));
      final byte[] block = new byte[blockSize];
      buffer.get(block);
      buffer.compact().position(0);
      final RLPInput oneBlock = new BytesValueRLPInput(BytesValue.wrap(block), false);
      oneBlock.enterList();
      hashes.add(BlockHeader.readFrom(oneBlock, MainnetBlockHashFunction::createHash).getHash());
      // We don't care about the bodies, just the headers
      oneBlock.skipNext();
      oneBlock.skipNext();
    }
    final MessageData initialMessage = GetBlockBodiesMessage.create(hashes);
    final MessageData raw = new RawMessage(EthPV62.GET_BLOCK_BODIES, initialMessage.getData());
    final GetBlockBodiesMessage message = GetBlockBodiesMessage.readFrom(raw);
    final Iterator<Hash> readHeaders = message.hashes().iterator();
    for (int i = 0; i < 50; ++i) {
      Assertions.assertThat(readHeaders.next()).isEqualTo(hashes.get(i));
    }
  }
}
