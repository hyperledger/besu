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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.web3j.protocol.besu.response.privacy.PrivateTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;
import org.web3j.utils.Restriction;

@SuppressWarnings("rawtypes")
public class PrivateLogFilterAcceptanceTest extends ParameterizedEnclaveTestBase {

  private final PrivacyNode node;

  public PrivateLogFilterAcceptanceTest(
      final Restriction restriction, final EnclaveType enclaveType) throws IOException {

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
  public void installAndUninstallFilter() {
    final String privacyGroupId = createPrivacyGroup();
    final EventEmitter eventEmitterContract = deployEventEmitterContract(privacyGroupId);

    final LogFilterJsonParameter filter =
        blockRangeLogFilter("earliest", "latest", eventEmitterContract.getContractAddress());

    final String filterId = node.execute(privacyTransactions.newFilter(privacyGroupId, filter));

    final boolean filterUninstalled =
        node.execute(privacyTransactions.uninstallFilter(privacyGroupId, filterId));

    assertThat(filterUninstalled).isTrue();
  }

  @Test
  public void getFilterLogs() {
    final String privacyGroupId = createPrivacyGroup();
    final EventEmitter eventEmitterContract = deployEventEmitterContract(privacyGroupId);

    final LogFilterJsonParameter filter =
        blockRangeLogFilter("earliest", "latest", eventEmitterContract.getContractAddress());
    final String filterId = node.execute(privacyTransactions.newFilter(privacyGroupId, filter));

    updateContractValue(privacyGroupId, eventEmitterContract, 1);

    final List<LogResult> logs =
        node.execute(privacyTransactions.getFilterLogs(privacyGroupId, filterId));

    assertThat(logs).hasSize(1);
  }

  @Test
  public void getFilterChanges() {
    final String privacyGroupId = createPrivacyGroup();
    final EventEmitter eventEmitterContract = deployEventEmitterContract(privacyGroupId);

    final LogFilterJsonParameter filter =
        blockRangeLogFilter("earliest", "latest", eventEmitterContract.getContractAddress());
    final String filterId = node.execute(privacyTransactions.newFilter(privacyGroupId, filter));

    updateContractValue(privacyGroupId, eventEmitterContract, 1);
    updateContractValue(privacyGroupId, eventEmitterContract, 2);

    assertThat(node.execute(privacyTransactions.getFilterChanges(privacyGroupId, filterId)))
        .hasSize(2);

    updateContractValue(privacyGroupId, eventEmitterContract, 3);

    assertThat(node.execute(privacyTransactions.getFilterChanges(privacyGroupId, filterId)))
        .hasSize(1);
  }

  private LogFilterJsonParameter blockRangeLogFilter(
      final String fromBlock, final String toBlock, final String contractAddress) {
    return new LogFilterJsonParameter(
        fromBlock, toBlock, List.of(contractAddress), Collections.emptyList(), null);
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
