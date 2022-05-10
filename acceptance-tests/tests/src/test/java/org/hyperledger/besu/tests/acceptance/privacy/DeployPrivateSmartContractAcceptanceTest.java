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

import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveEncryptorType;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.util.Optional;

import org.junit.Test;
import org.web3j.utils.Restriction;

public class DeployPrivateSmartContractAcceptanceTest extends ParameterizedEnclaveTestBase {

  private final PrivacyNode minerNode;

  public DeployPrivateSmartContractAcceptanceTest(
      final Restriction restriction,
      final EnclaveType enclaveType,
      final EnclaveEncryptorType enclaveEncryptorType)
      throws IOException {
    super(restriction, enclaveType, enclaveEncryptorType);

    minerNode =
        privacyBesu.createPrivateTransactionEnabledMinerNode(
            restriction + "-node",
            privacyAccountResolver.resolve(0, enclaveEncryptorType),
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED);

    privacyCluster.start(minerNode);
  }

  @Test
  public void deployingMustGiveValidReceiptAndCode() throws Exception {
    final String contractAddress =
        EnclaveEncryptorType.EC.equals(enclaveEncryptorType)
            ? "0xfeeb2367e77e28f75fc3bcc55b70a535752db058"
            : "0x89ce396d0f9f937ddfa71113e29b2081c4869555";

    final EventEmitter eventEmitter =
        minerNode.execute(
            privateContractTransactions.createSmartContract(
                EventEmitter.class,
                minerNode.getTransactionSigningKey(),
                minerNode.getEnclaveKey()));

    privateContractVerifier
        .validPrivateContractDeployed(contractAddress, minerNode.getAddress().toString())
        .verify(eventEmitter);

    privateContractVerifier.validContractCodeProvided().verify(eventEmitter);
  }
}
