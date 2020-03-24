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
package org.hyperledger.besu.tests.web3j.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyAcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.besu.response.privacy.PrivacyGroup;
import org.web3j.utils.Base64String;

public class PrivGetCodeAcceptanceTest extends PrivacyAcceptanceTestBase {

  private PrivacyNode alice;

  @Before
  public void setUp() throws Exception {
    alice =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            "alice", privacyAccountResolver.resolve(0));
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
                POW_CHAIN_ID,
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
        alice.execute(
            privacyTransactions.createPrivacyGroup("myGroupName", "my group description", alice));

    assertThat(privacyGroupId).isNotNull();

    final PrivacyGroup expected =
        new PrivacyGroup(
            privacyGroupId,
            PrivacyGroup.Type.PANTHEON,
            "myGroupName",
            "my group description",
            Base64String.wrapList(alice.getEnclaveKey()));

    alice.verify(privateTransactionVerifier.validPrivacyGroupCreated(expected));

    return privacyGroupId;
  }
}
