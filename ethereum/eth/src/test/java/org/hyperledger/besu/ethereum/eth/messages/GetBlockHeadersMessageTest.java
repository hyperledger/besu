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
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public final class GetBlockHeadersMessageTest {

  @Test
  public void roundTripWithHash() {
    for (final boolean reverse : Arrays.asList(true, false)) {
      final Hash hash = Hash.hash(Bytes.wrap(new byte[10]));
      final int skip = 10;
      final int maxHeaders = 128;
      final GetBlockHeadersMessage initialMessage =
          GetBlockHeadersMessage.create(hash, maxHeaders, skip, reverse);
      final MessageData raw = new RawMessage(EthPV62.GET_BLOCK_HEADERS, initialMessage.getData());
      final GetBlockHeadersMessage message = GetBlockHeadersMessage.readFrom(raw);
      Assertions.assertThat(message.blockNumber()).isEmpty();
      Assertions.assertThat(message.hash().get()).isEqualTo(hash);
      Assertions.assertThat(message.reverse()).isEqualTo(reverse);
      Assertions.assertThat(message.skip()).isEqualTo(skip);
      Assertions.assertThat(message.maxHeaders()).isEqualTo(maxHeaders);
    }
  }

  @Test
  public void roundTripBlockNum() {
    for (final boolean reverse : Arrays.asList(true, false)) {
      final long blockNum = 1000L;
      final int skip = 10;
      final int maxHeaders = 128;
      final GetBlockHeadersMessage initialMessage =
          GetBlockHeadersMessage.create(blockNum, maxHeaders, skip, reverse);
      final MessageData raw = new RawMessage(EthPV62.GET_BLOCK_HEADERS, initialMessage.getData());
      final GetBlockHeadersMessage message = GetBlockHeadersMessage.readFrom(raw);
      Assertions.assertThat(message.blockNumber().getAsLong()).isEqualTo(blockNum);
      Assertions.assertThat(message.hash()).isEmpty();
      Assertions.assertThat(message.reverse()).isEqualTo(reverse);
      Assertions.assertThat(message.skip()).isEqualTo(skip);
      Assertions.assertThat(message.maxHeaders()).isEqualTo(maxHeaders);
    }
  }
}
