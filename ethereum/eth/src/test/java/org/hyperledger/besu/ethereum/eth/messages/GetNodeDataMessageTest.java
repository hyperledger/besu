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
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public final class GetNodeDataMessageTest {

  @Test
  public void roundTripTest() {
    // Generate some hashes
    final BlockDataGenerator gen = new BlockDataGenerator(1);
    final List<Hash> hashes = new ArrayList<>();
    final int hashCount = 20;
    for (int i = 0; i < hashCount; ++i) {
      hashes.add(gen.hash());
    }

    // Perform round-trip transformation
    // Create GetNodeData, copy it to a generic message, then read back into a GetNodeData message
    final MessageData initialMessage = GetNodeDataMessage.create(hashes);
    final MessageData raw = new RawMessage(EthPV63.GET_NODE_DATA, initialMessage.getData());
    final GetNodeDataMessage message = GetNodeDataMessage.readFrom(raw);

    // Read hashes back out after round trip and check they match originals.
    final Iterator<Hash> readData = message.hashes().iterator();
    for (int i = 0; i < hashCount; ++i) {
      Assertions.assertThat(readData.next()).isEqualTo(hashes.get(i));
    }
    Assertions.assertThat(readData.hasNext()).isFalse();
  }
}
