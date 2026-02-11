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
package org.hyperledger.besu.tests.acceptanceqbft.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthCallTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthEstimateGasTransaction;
import org.hyperledger.besu.tests.web3j.generated.TestDepth;

import java.math.BigInteger;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class EthEstimateGasZeroToleranceAcceptanceTest extends AcceptanceTestBase {
  private static final BigInteger GAS_PRICE = BigInteger.valueOf(1000000000000L);
  final List<SimpleEntry<Integer, Long>> testCase = new ArrayList<>();

  @Test
  public void estimateGasWithDelegateCall() throws Exception {
    // there's only one test method so it's more efficient to have the setup in here
    final BesuNode node =
        besu.createQbftNode(
            "node1", b -> b.extraCLIOptions(List.of("--estimate-gas-tolerance-ratio=0.0")));

    cluster.start(node);
    final var deployContract = contractTransactions.createSmartContract(TestDepth.class);
    deployContract.setGasPrice(GAS_PRICE);
    final TestDepth testDepth = node.execute(deployContract);

    // taken from geth
    testCase.add(new SimpleEntry<>(1, 45554L));
    testCase.add(new SimpleEntry<>(2, 47387L));
    testCase.add(new SimpleEntry<>(3, 49249L));
    testCase.add(new SimpleEntry<>(4, 51141L));
    testCase.add(new SimpleEntry<>(5, 53063L));
    testCase.add(new SimpleEntry<>(10, 63139L));
    testCase.add(new SimpleEntry<>(65, 246462L));

    for (var test : testCase) {
      var functionCall = testDepth.depth(BigInteger.valueOf(test.getKey())).encodeFunctionCall();

      var estimateGas =
          node.execute(new EthEstimateGasTransaction(testDepth.getContractAddress(), functionCall));

      // Sanity check our estimate is good with eth_call
      var ethCall =
          node.execute(
              new EthCallTransaction(
                  testDepth.getContractAddress(), functionCall, estimateGas.getAmountUsed()));

      assertThat(ethCall.isReverted()).isEqualTo(false);

      // Zero tolerance means the estimate is right on the edge with eth_call
      var ethCallTooLow =
          node.execute(
              new EthCallTransaction(
                  testDepth.getContractAddress(),
                  functionCall,
                  estimateGas.getAmountUsed().subtract(BigInteger.ONE)));

      assertThat(ethCallTooLow.isReverted()).isEqualTo(true);

      // Zero tolerance means the estimate is right on the edge with eth_sendRawTransaction
      var transactionTooLow =
          node.execute(
              contractTransactions.callSmartContract(
                  testDepth.getContractAddress(),
                  functionCall,
                  estimateGas.getAmountUsed().subtract(BigInteger.ONE),
                  GAS_PRICE));

      node.verify(eth.expectSuccessfulTransactionReceipt(transactionTooLow.getTransactionHash()));

      var receiptTooLow =
          node.execute(
              ethTransactions.getTransactionReceiptWithRevertReason(
                  transactionTooLow.getTransactionHash()));

      assertThat(receiptTooLow).isPresent();
      assertThat(receiptTooLow.get().isStatusOK()).isFalse();

      // check our estimate will actually get mined successfully!
      var transaction =
          node.execute(
              contractTransactions.callSmartContract(
                  testDepth.getContractAddress(),
                  functionCall,
                  estimateGas.getAmountUsed(),
                  GAS_PRICE));

      node.verify(eth.expectSuccessfulTransactionReceipt(transaction.getTransactionHash()));

      var receipt =
          node.execute(ethTransactions.getTransactionReceipt(transaction.getTransactionHash()));

      assertThat(receipt).isPresent();
      assertThat(receipt.get().getGasUsed()).isLessThan(BigInteger.valueOf(test.getValue()));
      assertThat(receipt.get().isStatusOK()).isTrue();
    }
  }
}
