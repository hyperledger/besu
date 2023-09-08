/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.services.TraceService;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
class TraceServiceImplTest {

  TraceService traceService;
  private MutableBlockchain blockchain;
  private WorldStateArchive worldStateArchive;
  private BlockchainQueries blockchainQueries;

  /**
   * The blockchain for testing has a height of 32 and the account
   * 0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b makes a transaction per block. So, in the head, the
   * nonce of this account should be 32.
   */
  @BeforeEach
  public void setup() {
    final BlockchainSetupUtil blockchainSetupUtil =
        BlockchainSetupUtil.forTesting(DataStorageFormat.BONSAI);
    blockchainSetupUtil.importAllBlocks();
    blockchain = blockchainSetupUtil.getBlockchain();
    worldStateArchive = blockchainSetupUtil.getWorldArchive();
    blockchainQueries = new BlockchainQueries(blockchain, worldStateArchive);
    traceService =
        new TraceServiceImpl(blockchainQueries, blockchainSetupUtil.getProtocolSchedule());
  }

  @Test
  void shouldRetrieveStateUpdatePostTracingForOneBlock() {

    final Address addressToVerify =
        Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");

    final long persistedNonceForAccount =
        worldStateArchive.getMutable().get(addressToVerify).getNonce();

    traceService.trace(
        2,
        2,
        worldState -> {
          assertThat(worldState.get(addressToVerify).getNonce()).isEqualTo(1);
        },
        worldState -> {
          assertThat(worldState.get(addressToVerify).getNonce()).isEqualTo(2);
        },
        BlockAwareOperationTracer.NO_TRACING);

    assertThat(worldStateArchive.getMutable().get(addressToVerify).getNonce())
        .isEqualTo(persistedNonceForAccount);
  }

  @Test
  void shouldRetrieveStateUpdatePostTracingForAllBlocks() {
    final Address addressToVerify =
        Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");

    final long persistedNonceForAccount =
        worldStateArchive.getMutable().get(addressToVerify).getNonce();

    traceService.trace(
        0,
        32,
        worldState -> {
          assertThat(worldState.get(addressToVerify).getNonce()).isEqualTo(0);
        },
        worldState -> {
          assertThat(worldState.get(addressToVerify).getNonce())
              .isEqualTo(persistedNonceForAccount);
        },
        BlockAwareOperationTracer.NO_TRACING);

    assertThat(worldStateArchive.getMutable().get(addressToVerify).getNonce())
        .isEqualTo(persistedNonceForAccount);
  }
}
