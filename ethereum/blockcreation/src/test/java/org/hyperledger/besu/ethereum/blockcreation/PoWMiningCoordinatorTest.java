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
package org.hyperledger.besu.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_REMOTE_SEALERS_LIMIT;
import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_REMOTE_SEALERS_TTL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.PoWSolution;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;

public class PoWMiningCoordinatorTest {

  private final ExecutionContextTestFixture executionContext = ExecutionContextTestFixture.create();
  private final SyncState syncState = mock(SyncState.class);
  private final PoWMinerExecutor executor = mock(PoWMinerExecutor.class);
  private final PoWBlockMiner miner = mock(PoWBlockMiner.class);

  @Before
  public void setUp() {
    when(syncState.isInSync()).thenReturn(true);
  }

  @Test
  public void miningCoordinatorIsCreatedDisabledWithNoReportableMiningStatistics() {
    final PoWMiningCoordinator miningCoordinator =
        new PoWMiningCoordinator(
            executionContext.getBlockchain(),
            executor,
            syncState,
            DEFAULT_REMOTE_SEALERS_LIMIT,
            DEFAULT_REMOTE_SEALERS_TTL);
    final PoWSolution solution = new PoWSolution(1L, Hash.EMPTY, null, Bytes32.ZERO);

    assertThat(miningCoordinator.isMining()).isFalse();
    assertThat(miningCoordinator.hashesPerSecond()).isEqualTo(Optional.empty());
    assertThat(miningCoordinator.getWorkDefinition()).isEqualTo(Optional.empty());
    assertThat(miningCoordinator.submitWork(solution)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void reportedHashRateIsCachedIfNoCurrentDataInMiner() {
    final Optional<Long> hashRate1 = Optional.of(10L);
    final Optional<Long> hashRate2 = Optional.empty();
    final Optional<Long> hashRate3 = Optional.of(20L);

    when(miner.getHashesPerSecond()).thenReturn(hashRate1, hashRate2, hashRate3);

    when(executor.startAsyncMining(any(), any(), any())).thenReturn(Optional.of(miner));

    final PoWMiningCoordinator miningCoordinator =
        new PoWMiningCoordinator(
            executionContext.getBlockchain(),
            executor,
            syncState,
            DEFAULT_REMOTE_SEALERS_LIMIT,
            DEFAULT_REMOTE_SEALERS_TTL);

    // Must enable prior returning data
    miningCoordinator.enable();
    miningCoordinator.start();

    assertThat(miningCoordinator.hashesPerSecond()).isEqualTo(hashRate1);
    assertThat(miningCoordinator.hashesPerSecond()).isEqualTo(hashRate1);
    assertThat(miningCoordinator.hashesPerSecond()).isEqualTo(hashRate3);
  }
}
