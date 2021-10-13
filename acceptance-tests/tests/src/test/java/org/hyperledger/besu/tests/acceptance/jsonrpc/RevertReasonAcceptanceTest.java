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
package org.hyperledger.besu.tests.acceptance.jsonrpc;

import static org.hyperledger.besu.tests.web3j.generated.RevertReason.FUNC_REVERTWITHOUTREVERTREASON;
import static org.hyperledger.besu.tests.web3j.generated.RevertReason.FUNC_REVERTWITHREVERTREASON;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.web3j.generated.RevertReason;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.core.methods.response.EthSendTransaction;

public class RevertReasonAcceptanceTest extends AcceptanceTestBase {

  private BesuNode minerNode;

  @Before
  public void setUp() throws Exception {
    minerNode = besu.createMinerNodeWithRevertReasonEnabled("miner-node-withRevertReason");
    cluster.start(minerNode);
  }

  @Test
  public void mustRevertWithRevertReason() {
    final RevertReason revertReasonContract =
        minerNode.execute(contractTransactions.createSmartContract(RevertReason.class));
    final EthSendTransaction transaction =
        minerNode.execute(
            contractTransactions.callSmartContract(
                FUNC_REVERTWITHREVERTREASON, revertReasonContract.getContractAddress()));
    minerNode.verify(
        eth.expectSuccessfulTransactionReceiptWithReason(
            transaction.getTransactionHash(), "RevertReason"));
  }

  @Test
  public void mustRevertWithoutRevertReason() {
    final RevertReason revertReasonContract =
        minerNode.execute(contractTransactions.createSmartContract(RevertReason.class));
    final EthSendTransaction transaction =
        minerNode.execute(
            contractTransactions.callSmartContract(
                FUNC_REVERTWITHOUTREVERTREASON, revertReasonContract.getContractAddress()));
    minerNode.verify(
        eth.expectSuccessfulTransactionReceiptWithoutReason(transaction.getTransactionHash()));
  }
}
