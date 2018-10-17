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

import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.RawMessage;
import tech.pegasys.pantheon.ethereum.testutil.BlockDataGenerator;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.netty.buffer.ByteBuf;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class NodeDataMessageTest {

  @Test
  public void roundTripTest() throws IOException {
    // Generate some data
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final List<BytesValue> nodeData = new ArrayList<>();
    final int nodeCount = 20;
    for (int i = 0; i < nodeCount; ++i) {
      nodeData.add(gen.bytesValue());
    }

    // Perform round-trip transformation
    // Create specific message, copy it to a generic message, then read back into a specific format
    final MessageData initialMessage = NodeDataMessage.create(nodeData);
    final ByteBuf rawBuffer = NetworkMemoryPool.allocate(initialMessage.getSize());
    initialMessage.writeTo(rawBuffer);
    final MessageData raw = new RawMessage(EthPV63.NODE_DATA, rawBuffer);
    final NodeDataMessage message = NodeDataMessage.readFrom(raw);

    // Read data back out after round trip and check they match originals.
    try {
      final Iterator<BytesValue> readData = message.nodeData().iterator();
      for (int i = 0; i < nodeCount; ++i) {
        Assertions.assertThat(readData.next()).isEqualTo(nodeData.get(i));
      }
      Assertions.assertThat(readData.hasNext()).isFalse();
    } finally {
      message.release();
      initialMessage.release();
      raw.release();
    }
  }
}
