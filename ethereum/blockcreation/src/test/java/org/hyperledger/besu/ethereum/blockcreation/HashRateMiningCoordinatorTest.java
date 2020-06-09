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
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.ethereum.blockcreation.AbstractMiningCoordinatorTest.TestMiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HashRateMiningCoordinatorTest {
  private final Blockchain blockchain = mock(Blockchain.class);
  private final SyncState syncState = mock(SyncState.class);
  private final EthHashMinerExecutor minerExecutor = mock(EthHashMinerExecutor.class);
  private final String id;
  private final Long hashRate;
  private final int startSealersSize;
  private final boolean wantAdded;
  private final int wantSize;
  private final boolean wantCheckSealerInfo;
  private final String wantSealerID;

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"1", 1L, 0, true, 1, false, null},
          {"1", 0L, 0, false, 0, false, null},
          {"1", 1L, DEFAULT_REMOTE_SEALERS_LIMIT, true, DEFAULT_REMOTE_SEALERS_LIMIT, true, "1"},
        });
  }

  public HashRateMiningCoordinatorTest(
      final String id,
      final Long hashRate,
      final int startSealersSize,
      final boolean wantAdded,
      final int wantSize,
      final boolean wantCheckSealerInfo,
      final String wantSealerID) {
    this.id = id;
    this.hashRate = hashRate;
    this.startSealersSize = startSealersSize;
    this.wantAdded = wantAdded;
    this.wantSize = wantSize;
    this.wantCheckSealerInfo = wantCheckSealerInfo;
    this.wantSealerID = wantSealerID;
  }

  @Test
  public void test() {
    final TestMiningCoordinator miningCoordinator =
        new TestMiningCoordinator(blockchain, minerExecutor, syncState);
    for (int i = 0; i < startSealersSize; i++) {
      miningCoordinator.submitHashRate(
          UUID.randomUUID().toString(), ThreadLocalRandom.current().nextLong());
    }
    assertThat(miningCoordinator.submitHashRate(id, hashRate)).isEqualTo(wantAdded);
    assertThat(miningCoordinator.getSealerInfos().size()).isEqualTo(wantSize);
    if (wantCheckSealerInfo) {
      assertThat(miningCoordinator.getSealerInfos()).containsKey(wantSealerID);
    }
  }
}
