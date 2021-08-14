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
import static org.web3j.utils.Restriction.RESTRICTED;
import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.ParameterizedEnclaveTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.PrivacyNode;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.account.PrivacyAccountResolver;
import org.hyperledger.besu.tests.web3j.generated.EventEmitter;
import org.hyperledger.enclave.testutil.EnclaveType;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.utils.Restriction;

@RunWith(Parameterized.class)
public class PrivateGenesisAcceptanceTest extends ParameterizedEnclaveTestBase {
  private final PrivacyNode alice;

  public PrivateGenesisAcceptanceTest(final Restriction restriction, final EnclaveType enclaveType)
      throws IOException {

    super(restriction, enclaveType);

    alice =
        privacyBesu.createIbft2NodePrivacyEnabledWithGenesis(
            "node1",
            PrivacyAccountResolver.ALICE,
            true,
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED,
            "AA");

    privacyCluster.start(alice);
  }

  @Test
  public void canInteractWithPrivateGenesisPreCompile() throws Exception {
    final String privacyGroupId = createPrivacyGroup();

    final EventEmitter eventEmitter =
        alice.execute(
            privateContractTransactions.loadSmartContractWithPrivacyGroupId(
                "0x1000000000000000000000000000000000000001",
                EventEmitter.class,
                alice.getTransactionSigningKey(),
                alice.getEnclaveKey(),
                privacyGroupId));

    eventEmitter.store(BigInteger.valueOf(42)).send();

    final EthCall response =
        alice.execute(
            privacyTransactions.privCall(
                privacyGroupId, eventEmitter, eventEmitter.value().encodeFunctionCall()));

    final String value = response.getValue();

    assertThat(new BigInteger(value.substring(2), 16)).isEqualByComparingTo(BigInteger.valueOf(42));
  }

  private String createPrivacyGroup() {
    if (restriction == RESTRICTED) {
      return alice.execute(privacyTransactions.createPrivacyGroup("name", "description", alice));
    } else if (restriction == UNRESTRICTED) {
      return "gsvwYfGPurL7wgXKmgFtCamXarAl9fA5jaSXi8TLpJw=";
    } else {
      throw new RuntimeException("Do not know how to handle " + restriction);
    }
  }
}
