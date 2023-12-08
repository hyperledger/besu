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
package org.hyperledger.besu.tests.acceptance.dsl.privacy;

import static org.hyperledger.enclave.testutil.EnclaveEncryptorType.EC;
import static org.hyperledger.enclave.testutil.EnclaveEncryptorType.NACL;
import static org.hyperledger.enclave.testutil.EnclaveType.NOOP;
import static org.hyperledger.enclave.testutil.EnclaveType.TESSERA;
import static org.web3j.utils.Restriction.RESTRICTED;
import static org.web3j.utils.Restriction.UNRESTRICTED;

import org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction.PluginCreateRandomPrivacyGroupIdTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.transaction.RestrictedCreatePrivacyGroupTransaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.enclave.testutil.EnclaveEncryptorType;

import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;
import org.web3j.utils.Restriction;

public abstract class ParameterizedEnclaveTestBase extends PrivacyAcceptanceTestBase {

  public static Stream<Arguments> params() {
    return Stream.of(
        Arguments.of(RESTRICTED, TESSERA, NACL),
        Arguments.of(RESTRICTED, TESSERA, EC),
        Arguments.of(UNRESTRICTED, NOOP, EnclaveEncryptorType.NOOP));
  }

  public Transaction<String> createPrivacyGroup(
      final Restriction restriction,
      final String name,
      final String description,
      final PrivacyNode... nodes) {

    if (restriction == RESTRICTED) {
      return new RestrictedCreatePrivacyGroupTransaction(name, description, nodes);
    } else if (restriction == UNRESTRICTED) {
      return new PluginCreateRandomPrivacyGroupIdTransaction();
    } else {
      throw new RuntimeException("Do not know how to handle " + restriction);
    }
  }
}
