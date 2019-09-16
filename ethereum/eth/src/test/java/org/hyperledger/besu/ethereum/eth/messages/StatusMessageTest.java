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
package org.hyperledger.besu.ethereum.eth.messages;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.uint.UInt256;

import java.math.BigInteger;
import java.util.Random;

import org.junit.Test;

public class StatusMessageTest {

  @Test
  public void getters() {
    final int version = EthProtocol.EthVersion.V62;
    final BigInteger networkId = BigInteger.ONE;
    final UInt256 td = UInt256.of(1000L);
    final Hash bestHash = randHash(1L);
    final Hash genesisHash = randHash(2L);

    final StatusMessage msg = StatusMessage.create(version, networkId, td, bestHash, genesisHash);

    assertThat(msg.protocolVersion()).isEqualTo(version);
    assertThat(msg.networkId()).isEqualTo(networkId);
    assertThat(msg.totalDifficulty()).isEqualTo(td);
    assertThat(msg.bestHash()).isEqualTo(bestHash);
    assertThat(msg.genesisHash()).isEqualTo(genesisHash);
  }

  @Test
  public void serializeDeserialize() {
    final int version = EthProtocol.EthVersion.V62;
    final BigInteger networkId = BigInteger.ONE;
    final UInt256 td = UInt256.of(1000L);
    final Hash bestHash = randHash(1L);
    final Hash genesisHash = randHash(2L);

    final MessageData msg = StatusMessage.create(version, networkId, td, bestHash, genesisHash);

    // Make a message copy from serialized data and check deserialized results
    final StatusMessage copy = new StatusMessage(msg.getData());

    assertThat(copy.protocolVersion()).isEqualTo(version);
    assertThat(copy.networkId()).isEqualTo(networkId);
    assertThat(copy.totalDifficulty()).isEqualTo(td);
    assertThat(copy.bestHash()).isEqualTo(bestHash);
    assertThat(copy.genesisHash()).isEqualTo(genesisHash);
  }

  private Hash randHash(final long seed) {
    final Random random = new Random(seed);
    final byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Hash.wrap(Bytes32.wrap(bytes));
  }
}
