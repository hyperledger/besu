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
package org.hyperledger.besu.tests.acceptance.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.util.LogFilterJsonParameter;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;
import org.web3j.utils.Restriction;

@SuppressWarnings("rawtypes")
public class PrivGetLogsAcceptanceTest extends ParameterizedEnclaveTestBase {

  /*
   This value is derived from the contract event signature
  */
  private static final String EVENT_EMITTER_EVENT_TOPIC =
      "0xc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5";

  private final PrivacyNode node;

  public PrivGetLogsAcceptanceTest(final Restriction restriction, final EnclaveType enclaveType)
      throws IOException {

    super(restriction, enclaveType);

    node =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            restriction + "-node",
            PrivacyAccountResolver.ALICE,
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED);

    privacyCluster.start(node);
  }

  @Test
  public void getLogsUsingBlockRangeFilter() {
    final String privacyGroupId = createPrivacyGroup();
    final EventEmitter eventEmitterContract = deployEventEmitterContract(privacyGroupId);

    /*
     Updating the contract value 2 times
    */
    updateContractValue(privacyGroupId, eventEmitterContract, 1);
    updateContractValue(privacyGroupId, eventEmitterContract, 2);

    final LogFilterJsonParameter filter =
        blockRangeLogFilter("earliest", "latest", eventEmitterContract.getContractAddress());

    final List<LogResult> logs =
        node.execute(privacyTransactions.privGetLogs(privacyGroupId, filter));

    /*
     We expect one log entry per tx changing the contract value
    */
    assertThat(logs).hasSize(2);
  }

  @Test
  public void getLogsUsingBlockHashFilter() {
    final String privacyGroupId = createPrivacyGroup();
    final EventEmitter eventEmitterContract = deployEventEmitterContract(privacyGroupId);

    /*
     Updating the contract value 1 times
    */
    final PrivateTransactionReceipt updateValueReceipt =
        updateContractValue(privacyGroupId, eventEmitterContract, 1);
    final String blockHash = updateValueReceipt.getBlockHash();

    final LogFilterJsonParameter filter =
        blockHashLogFilter(blockHash, eventEmitterContract.getContractAddress());

    final List<LogResult> logs =
        node.execute(privacyTransactions.privGetLogs(privacyGroupId, filter));

    assertThat(logs).hasSize(1);
  }

  private LogFilterJsonParameter blockRangeLogFilter(
      final String fromBlock, final String toBlock, final String contractAddress) {
    return new LogFilterJsonParameter(
        fromBlock,
        toBlock,
        List.of(contractAddress),
        List.of(List.of(EVENT_EMITTER_EVENT_TOPIC)),
        null);
  }

  private LogFilterJsonParameter blockHashLogFilter(
      final String blockHash, final String contractAddress) {
    return new LogFilterJsonParameter(
        null,
        null,
        List.of(contractAddress),
        List.of(List.of(EVENT_EMITTER_EVENT_TOPIC)),
        blockHash);
  }

  private String createPrivacyGroup() {
    return node.execute(createPrivacyGroup("myGroupName", "my group description", node));
  }

  private EventEmitter deployEventEmitterContract(final String privacyGroupId) {
    final EventEmitter eventEmitter =
        node.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                node.getTransactionSigningKey(),
                restriction,
                node.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            eventEmitter.getContractAddress(), node.getAddress().toString())
        .verify(eventEmitter);

    return eventEmitter;
  }

  private PrivateTransactionReceipt updateContractValue(
      final String privacyGroupId, final EventEmitter eventEmitterContract, final int value) {
    final String transactionHash =
        node.execute(
            privateContractTransactions.callSmartContractWithPrivacyGroupId(
                eventEmitterContract.getContractAddress(),
                eventEmitterContract.store(BigInteger.valueOf(value)).encodeFunctionCall(),
                node.getTransactionSigningKey(),
                restriction,
                node.getEnclaveKey(),
                privacyGroupId));

    return node.execute(privacyTransactions.getPrivateTransactionReceipt(transactionHash));
  }
}
