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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.web3j.utils.Restriction;

public class PrivGetCodeAcceptanceTest extends ParameterizedEnclaveTestBase {

  private final PrivacyNode alice;

  public PrivGetCodeAcceptanceTest(final Restriction restriction, final EnclaveType enclaveType)
      throws IOException {

    super(restriction, enclaveType);

    alice =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            restriction + "-node",
            PrivacyAccountResolver.ALICE,
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED);

    privacyCluster.start(alice);
  }

  @Test
  public void privGetCodeReturnsDeployedContractBytecode() {
    final String privacyGroupId = createPrivacyGroup();
    final EventEmitter eventEmitterContract = deployPrivateContract(privacyGroupId);

    final Bytes deployedContractCode =
        alice.execute(
            privacyTransactions.privGetCode(
                privacyGroupId,
                Address.fromHexString(eventEmitterContract.getContractAddress()),
                "latest"));

    assertThat(eventEmitterContract.getContractBinary())
        .contains(deployedContractCode.toUnprefixedHexString());
  }

  private EventEmitter deployPrivateContract(final String privacyGroupId) {
    final EventEmitter eventEmitter =
        alice.execute(
            privateContractTransactions.createSmartContractWithPrivacyGroupId(
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                restriction,
                alice.getEnclaveKey(),
                privacyGroupId));

    privateContractVerifier
        .validPrivateContractDeployed(
            eventEmitter.getContractAddress(), alice.getAddress().toString())
        .verify(eventEmitter);
    return eventEmitter;
  }

  private String createPrivacyGroup() {
    final String privacyGroupId =
        alice.execute(createPrivacyGroup("myGroupName", "my group description", alice));

    assertThat(privacyGroupId).isNotNull();

    return privacyGroupId;
  }
}
