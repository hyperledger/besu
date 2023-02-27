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
package org.hyperledger.besu.tests.acceptance.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthEstimateGasTransaction;
import org.hyperledger.besu.tests.web3j.generated.TestDepth;

import java.math.BigInteger;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.core.methods.response.EthEstimateGas;

public class EthEstimateGasAcceptanceTest extends AcceptanceTestBase {

  private BesuNode node;
  private TestDepth testDepth;

  @Before
  public void setUp() throws Exception {
    node = besu.createMinerNode("node1");
    cluster.start(node);
    testDepth = node.execute(contractTransactions.createSmartContract(TestDepth.class));
  }

  @Test
  public void estimateGasWithDelegateCall() {

    List<SimpleEntry<Integer, Long>> testCase = new ArrayList<>();
    testCase.add(new SimpleEntry<>(1, 45554L));
    testCase.add(new SimpleEntry<>(2, 47387L));
    testCase.add(new SimpleEntry<>(3, 49249L));
    testCase.add(new SimpleEntry<>(4, 51141L));
    testCase.add(new SimpleEntry<>(5, 53063L));
    testCase.add(new SimpleEntry<>(10, 63139L));
    testCase.add(new SimpleEntry<>(65, 246462L));
    // taken from geth

    for (var test : testCase) {
      var functionCall = testDepth.depth(BigInteger.valueOf(test.getKey())).encodeFunctionCall();
      var ethEstimateGas =
          new EthEstimateGasTransaction(testDepth.getContractAddress(), functionCall);

      final EthEstimateGas response = node.execute(ethEstimateGas);
      assertThat(response.getAmountUsed())
          .withFailMessage(
              "Depth %d expected gasEstimate %d but was %dL",
              test.getKey(), test.getValue(), response.getAmountUsed())
          .isEqualTo(BigInteger.valueOf(test.getValue()));
    }
  }
}
