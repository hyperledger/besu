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
package org.hyperledger.besu.tests.acceptance.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.web3j.generated.SimpleStorage;
import org.hyperledger.enclave.testutil.EnclaveEncryptorType;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import org.junit.Test;
import org.web3j.utils.Restriction;

public class PrivTraceTransactionAcceptanceTest extends ParameterizedEnclaveTestBase {

  private final PrivacyNode node;

  public PrivTraceTransactionAcceptanceTest(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws IOException {

    super(restriction, enclaveType, enclaveEncryptorType);

    node =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            restriction + "-node",
            PrivacyAccountResolver.ALICE.resolve(enclaveEncryptorType),
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED);

    privacyCluster.start(node);
  }

  @Test
  public void getTransactionTrace() {
    final String privacyGroupId = createPrivacyGroup();
    final SimpleStorage simpleStorageContract = deploySimpleStorageContract(privacyGroupId);

    /*
     Updating the contract value
    */
    Hash transactionHash =
        Hash.fromHexString(doTransaction(privacyGroupId, simpleStorageContract, 1));

    final String result =
        node.execute(privacyTransactions.privTraceTransaction(privacyGroupId, transactionHash));

    System.out.println("privTransactionTrace = " + result);
    assertThat(result).isNotNull();
  }

  private String createPrivacyGroup() {
    return node.execute(createPrivacyGroup("myGroupName", "my group description", node));
  }

  private SimpleStorage deploySimpleStorageContract(final String privacyGroupId) {
    final SimpleStorage simpleStorage =
        node.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                SimpleStorage.class,
                node.getTransactionSigningKey(),
                restriction,
                node.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            simpleStorage.getContractAddress(), node.getAddress().toString())
        .verify(simpleStorage);

    return simpleStorage;
  }

  private String doTransaction(
      final String privacyGroupId, final SimpleStorage simpleStorageContract, final int value) {
    return node.execute(
        privateContractTransactions.callSmartContractWithPrivacyGroupId(
            simpleStorageContract.getContractAddress(),
            simpleStorageContract.set(BigInteger.valueOf(value)).encodeFunctionCall(),
            node.getTransactionSigningKey(),
            restriction,
            node.getEnclaveKey(),
            privacyGroupId));
  }
}
