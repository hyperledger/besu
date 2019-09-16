/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.eth.manager.DeterministicEthScheduler.TimeoutPolicy;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

public class EthContextTestUtil {

  private static final String PROTOCOL_NAME = "ETH";

  public static EthContext createTestEthContext(final TimeoutPolicy timeoutPolicy) {
    return new EthContext(
        new EthPeers(PROTOCOL_NAME, TestClock.fixed(), new NoOpMetricsSystem()),
        new EthMessages(),
        new DeterministicEthScheduler(timeoutPolicy));
  }
}
