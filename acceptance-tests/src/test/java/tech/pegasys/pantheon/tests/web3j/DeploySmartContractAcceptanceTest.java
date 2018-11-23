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
package tech.pegasys.pantheon.tests.web3j;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.web3j.generated.SimpleStorage;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

public class DeploySmartContractAcceptanceTest extends AcceptanceTestBase {

  private PantheonNode minerNode;

  @Before
  public void setUp() throws Exception {
    minerNode = pantheon.createMinerNode("miner-node");
    cluster.start(minerNode);
  }

  @Test
  public void deployContractReceiptMustMatchEthGetTransactionReceipt() {
    final SimpleStorage contract =
        minerNode.execute(transactions.createSmartContract(SimpleStorage.class));
    assertThat(contract).isNotNull();

    final Optional<TransactionReceipt> receipt = contract.getTransactionReceipt();
    assertThat(receipt).isNotNull();
    assertThat(receipt.isPresent()).isTrue();

    final TransactionReceipt transactionReceipt = receipt.get();
    assertThat(transactionReceipt.getTransactionHash()).isNotNull();

    // Contract transaction has no 'to' address or contract address
    assertThat(transactionReceipt.getTo()).isNull();
    assertThat(transactionReceipt.getContractAddress()).isNotBlank();

    minerNode.verify(
        eth.expectTransactionReceipt(transactionReceipt.getTransactionHash(), transactionReceipt));
  }
}
