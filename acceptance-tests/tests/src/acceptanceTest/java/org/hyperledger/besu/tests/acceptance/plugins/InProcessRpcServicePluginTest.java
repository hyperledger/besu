/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.tests.acceptance.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InProcessRpcServicePluginTest extends AcceptanceTestBase {
  private static final long MIN_GAS_PRICE = 123456;
  private BesuNode node;

  @BeforeEach
  public void setUp() throws Exception {
    node =
        besu.createPluginsNode(
            "node1",
            List.of("testPlugins"),
            List.of(
                "--Xin-process-rpc-enabled=true",
                "--Xin-process-rpc-apis=MINER",
                "--plugin-test-set-min-gas-price=" + MIN_GAS_PRICE),
            "MINER");
    cluster.start(node);
  }

  @Test
  public void smokeTest() {
    final var currMinGasPrice = node.execute(minerTransactions.minerGetMinGasPrice());
    assertThat(currMinGasPrice).isEqualTo(BigInteger.valueOf(MIN_GAS_PRICE));
  }
}
