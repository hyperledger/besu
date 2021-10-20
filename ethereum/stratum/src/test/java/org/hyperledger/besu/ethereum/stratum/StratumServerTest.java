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
package org.hyperledger.besu.ethereum.stratum;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.PoWSolverInputs;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.metrics.StubMetricsSystem;

import java.util.concurrent.CompletableFuture;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;
import org.mockito.Mockito;

public class StratumServerTest {

  @Test
  public void runJobSetDifficultyShouldSucceed() {
    StubMetricsSystem metrics = new StubMetricsSystem();
    stratumServerWithMocks(metrics).newJob(new PoWSolverInputs(UInt256.MAX_VALUE, Bytes.EMPTY, 1));
    assertThat(metrics.getGaugeValue("difficulty")).isEqualTo(1d);
  }

  @Test
  public void runJobSetZeroDifficultyShouldNotThrow() {
    StubMetricsSystem metrics = new StubMetricsSystem();
    stratumServerWithMocks(metrics).newJob(new PoWSolverInputs(UInt256.MIN_VALUE, Bytes.EMPTY, 1));

    assertThat(metrics.getGaugeValue("difficulty"))
        .isEqualTo(UInt256.MAX_VALUE.toUnsignedBigInteger().doubleValue());
  }

  private StratumServer stratumServerWithMocks(final ObservableMetricsSystem metrics) {
    PoWMiningCoordinator mockPoW =
        Mockito.when(Mockito.mock(PoWMiningCoordinator.class).getEpochCalculator())
            .thenReturn(new EpochCalculator.DefaultEpochCalculator())
            .getMock();

    StratumServer ss =
        new StratumServer(null, mockPoW, 0, "lo", "", metrics) {
          @Override
          public CompletableFuture<?> start() {
            this.started.set(true);
            return null;
          }
        };
    ss.start();
    return ss;
  }
}
