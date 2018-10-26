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

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.RawMessage;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class GetBlockHeadersMessageTest {

  @Test
  public void roundTripWithHash() {
    for (final boolean reverse : Arrays.asList(true, false)) {
      final Hash hash = Hash.hash(BytesValue.wrap(new byte[10]));
      final int skip = 10;
      final int maxHeaders = 128;
      final GetBlockHeadersMessage initialMessage =
          GetBlockHeadersMessage.create(hash, maxHeaders, skip, reverse);
      final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
      initialMessage.writeTo(rawBuffer);
      final MessageData raw = new RawMessage(EthPV62.GET_BLOCK_HEADERS, rawBuffer);
      final GetBlockHeadersMessage message = GetBlockHeadersMessage.readFrom(raw);
      try {
        Assertions.assertThat(message.blockNumber()).isEmpty();
        Assertions.assertThat(message.hash().get()).isEqualTo(hash);
        Assertions.assertThat(message.reverse()).isEqualTo(reverse);
        Assertions.assertThat(message.skip()).isEqualTo(skip);
        Assertions.assertThat(message.maxHeaders()).isEqualTo(maxHeaders);
      } finally {
        initialMessage.release();
        raw.release();
        message.release();
      }
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
      final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
      final MessageData raw = new RawMessage(EthPV62.GET_BLOCK_HEADERS, rawBuffer);
      final GetBlockHeadersMessage message = GetBlockHeadersMessage.readFrom(raw);
      try {
        Assertions.assertThat(initialMessage.blockNumber().getAsLong()).isEqualTo(blockNum);
        Assertions.assertThat(initialMessage.hash()).isEmpty();
        Assertions.assertThat(initialMessage.reverse()).isEqualTo(reverse);
        Assertions.assertThat(initialMessage.skip()).isEqualTo(skip);
        Assertions.assertThat(initialMessage.maxHeaders()).isEqualTo(maxHeaders);
      } finally {
        initialMessage.release();
        raw.release();
        message.release();
      }
    }
  }
}
