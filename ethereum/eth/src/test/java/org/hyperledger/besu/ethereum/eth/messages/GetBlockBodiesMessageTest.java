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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/** Tests for {@link GetBlockBodiesMessage}. */
public final class GetBlockBodiesMessageTest {

  @Test
  public void getBlockBodiesRoundTrip() throws IOException {
    final List<Hash> hashes = new ArrayList<>();
    final ByteBuffer buffer =
        ByteBuffer.wrap(Resources.toByteArray(this.getClass().getResource("/50.blocks")));
    for (int i = 0; i < 50; ++i) {
      final int blockSize = RLP.calculateSize(Bytes.wrapByteBuffer(buffer));
      final byte[] block = new byte[blockSize];
      buffer.get(block);
      buffer.compact().position(0);
      final RLPInput oneBlock = new BytesValueRLPInput(Bytes.wrap(block), false);
      oneBlock.enterList();
      hashes.add(BlockHeader.readFrom(oneBlock, new MainnetBlockHeaderFunctions()).getHash());
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
