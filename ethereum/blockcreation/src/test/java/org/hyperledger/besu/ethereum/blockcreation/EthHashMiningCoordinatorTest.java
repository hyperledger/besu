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
package org.hyperledger.besu.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.EthHashSolution;
import org.hyperledger.besu.util.bytes.Bytes32;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

public class EthHashMiningCoordinatorTest {

  private final ExecutionContextTestFixture executionContext = ExecutionContextTestFixture.create();
  private final SyncState syncState = mock(SyncState.class);
  private final EthHashMinerExecutor executor = mock(EthHashMinerExecutor.class);
  private final EthHashBlockMiner miner = mock(EthHashBlockMiner.class);

  @Before
  public void setUp() {
    when(syncState.isInSync()).thenReturn(true);
  }

  @Test
  public void miningCoordinatorIsCreatedDisabledWithNoReportableMiningStatistics() {
    final EthHashMiningCoordinator miningCoordinator =
        new EthHashMiningCoordinator(executionContext.getBlockchain(), executor, syncState);
    final EthHashSolution solution = new EthHashSolution(1L, Hash.EMPTY, new byte[Bytes32.SIZE]);

    assertThat(miningCoordinator.isRunning()).isFalse();
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

    when(executor.startAsyncMining(any(), any())).thenReturn(miner);

    final EthHashMiningCoordinator miningCoordinator =
        new EthHashMiningCoordinator(executionContext.getBlockchain(), executor, syncState);

    miningCoordinator.enable(); // Must enable prior returning data
    assertThat(miningCoordinator.hashesPerSecond()).isEqualTo(hashRate1);
    assertThat(miningCoordinator.hashesPerSecond()).isEqualTo(hashRate1);
    assertThat(miningCoordinator.hashesPerSecond()).isEqualTo(hashRate3);
  }
}
