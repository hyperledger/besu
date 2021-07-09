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

    // The code in the genesis file needs to be the deployed contract code, not the code to deploy
    // the contract
    // you can generate it using the solc --bin-runtime flag
    // cd ./acceptance-tests/tests/src/test/java/org/hyperledger/besu/tests/web3j/
    // docker run -v $PWD:/sources ethereum/solc:0.5.0 -o /sources/output --bin-runtime
    // /sources/EventEmitter.sol --overwrite

    alice =
        privacyBesu.createIbft2NodePrivacyEnabledWithPrivateGenesis(
            "node1",
            PrivacyAccountResolver.ALICE,
            true,
            enclaveType,
            Optional.empty(),
            false,
            false,
            restriction == UNRESTRICTED,
            "AA",
            "{"
                + "  \"alloc\": {"
                + "    \"0x1000000000000000000000000000000000000001\": {"
                + "      \"code\": \"0x608060405260043610610057576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff1680633fa4f2451461005c5780636057361d1461008757806367e404ce146100c2575b600080fd5b34801561006857600080fd5b50610071610119565b6040518082815260200191505060405180910390f35b34801561009357600080fd5b506100c0600480360360208110156100aa57600080fd5b8101908080359060200190929190505050610123565b005b3480156100ce57600080fd5b506100d76101d9565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b6000600254905090565b7fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f53382604051808373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020018281526020019250505060405180910390a18060028190555033600160006101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555050565b6000600160009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1690509056fea165627a7a72305820e74360c3d08936cb1747ad641729261ff5e83b6fc0d303d136e171f15f07d7740029\""
                + "    }"
                + "  }"
                + "}");
    privacyCluster.start(alice);
  }

  @Test
  public void privGetCodeReturnsDeployedContractBytecode() throws Exception {
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

  public String createPrivacyGroup() {
    if (restriction == RESTRICTED) {
      return alice.execute(privacyTransactions.createPrivacyGroup("name", "description", alice));
    } else if (restriction == UNRESTRICTED) {
      return "gsvwYfGPurL7wgXKmgFtCamXarAl9fA5jaSXi8TLpJw=";
    } else {
      throw new RuntimeException("Do not know how to handle " + restriction);
    }
  }
}
