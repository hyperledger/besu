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

import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.RawMessage;
import tech.pegasys.pantheon.ethereum.testutil.BlockDataGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.netty.buffer.ByteBuf;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class ReceiptsMessageTest {

  @Test
  public void roundTripTest() throws IOException {
    // Generate some data
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final List<List<TransactionReceipt>> receipts = new ArrayList<>();
    final int dataCount = 20;
    final int receiptsPerSet = 3;
    for (int i = 0; i < dataCount; ++i) {
      final List<TransactionReceipt> receiptSet = new ArrayList<>();
      for (int j = 0; j < receiptsPerSet; j++) {
        receiptSet.add(gen.receipt());
      }
      receipts.add(receiptSet);
    }

    // Perform round-trip transformation
    // Create specific message, copy it to a generic message, then read back into a specific format
    final MessageData initialMessage = ReceiptsMessage.create(receipts);
    final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
    initialMessage.writeTo(rawBuffer);
    final MessageData raw = new RawMessage(EthPV63.RECEIPTS, rawBuffer);
    final ReceiptsMessage message = ReceiptsMessage.readFrom(raw);

    // Read data back out after round trip and check they match originals.
    try {
      final Iterator<List<TransactionReceipt>> readData = message.receipts().iterator();
      for (int i = 0; i < dataCount; ++i) {
        Assertions.assertThat(readData.next()).isEqualTo(receipts.get(i));
      }
      Assertions.assertThat(readData.hasNext()).isFalse();
    } finally {
      message.release();
      initialMessage.release();
      raw.release();
    }
  }
}
