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
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HashRateMiningCoordinatorTest {
  private final Blockchain blockchain = mock(Blockchain.class);
  private final SyncState syncState = mock(SyncState.class);
  private final PoWMinerExecutor minerExecutor = mock(PoWMinerExecutor.class);
  private final String id;
  private final Long hashRate;
  private final Long wantTotalHashrate;
  private final int startSealersSize;
  private final boolean wantAdded;

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"1", 1L, 2L, 1, true},
          {"1", 1L, 1L, 0, true},
          {"1", 0L, 0L, 0, false},
          {"1", 1L, 501L, 500, true},
        });
  }

  public HashRateMiningCoordinatorTest(
      final String id,
      final long hashRate,
      final long wantTotalHashrate,
      final int startSealersSize,
      final boolean wantAdded) {
    this.id = id;
    this.hashRate = hashRate;
    this.startSealersSize = startSealersSize;
    this.wantAdded = wantAdded;
    this.wantTotalHashrate = wantTotalHashrate;
  }

  @Test
  public void test() {
    final PoWMiningCoordinator miningCoordinator =
        new PoWMiningCoordinator(blockchain, minerExecutor, syncState, 1000, 10);
    for (int i = 0; i < startSealersSize; i++) {
      miningCoordinator.submitHashRate(UUID.randomUUID().toString(), 1L);
    }
    assertThat(miningCoordinator.submitHashRate(id, hashRate)).isEqualTo(wantAdded);
    if (wantTotalHashrate == 0L) {
      assertThat(miningCoordinator.hashesPerSecond()).isEmpty();
    } else {
      assertThat(miningCoordinator.hashesPerSecond()).contains(wantTotalHashrate);
    }
  }
}
