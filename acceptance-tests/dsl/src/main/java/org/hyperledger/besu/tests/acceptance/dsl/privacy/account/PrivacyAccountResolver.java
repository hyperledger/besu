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
package org.hyperledger.besu.tests.acceptance.dsl.privacy.account;

import org.hyperledger.enclave.testutil.EnclaveEncryptorType;

import java.net.URL;

/** Supplier of known funded accounts defined in dev.json */
public class PrivacyAccountResolver {

  public static final PrivacyAccount MULTI_TENANCY =
      PrivacyAccount.create(
          resolveResource("key"),
          new URL[] {
            resolveResource("enclave_key_0.pub"),
            resolveResource("enclave_key_1.pub"),
            resolveResource("enclave_key_2.pub")
          },
          new URL[] {
            resolveResource("enclave_key_0.key"),
            resolveResource("enclave_key_1.key"),
            resolveResource("enclave_key_2.key")
          });

  public PrivacyAccountResolver() {}

  public PrivacyAccount resolve(
      final Integer account, final EnclaveEncryptorType enclaveEncryptorType) {
    switch (account) {
      case 0:
        // ALICE
        return PrivacyAccount.create(
            resolveResource("key"),
            enclaveEncryptorType.equals(EnclaveEncryptorType.EC)
                ? resolveResource("enclave_ec_key_0.pub")
                : resolveResource("enclave_key_0.pub"),
            enclaveEncryptorType.equals(EnclaveEncryptorType.EC)
                ? resolveResource("enclave_ec_key_0.key")
                : resolveResource("enclave_key_0.key"));
      case 1:
        // BOB
        return PrivacyAccount.create(
            resolveResource("key1"),
            enclaveEncryptorType.equals(EnclaveEncryptorType.EC)
                ? resolveResource("enclave_ec_key_1.pub")
                : resolveResource("enclave_key_1.pub"),
            enclaveEncryptorType.equals(EnclaveEncryptorType.EC)
                ? resolveResource("enclave_ec_key_1.key")
                : resolveResource("enclave_key_1.key"));
      case 2:
        // CHARLIE
        return PrivacyAccount.create(
            resolveResource("key2"),
            enclaveEncryptorType.equals(EnclaveEncryptorType.EC)
                ? resolveResource("enclave_ec_key_2.pub")
                : resolveResource("enclave_key_2.pub"),
            enclaveEncryptorType.equals(EnclaveEncryptorType.EC)
                ? resolveResource("enclave_ec_key_2.key")
                : resolveResource("enclave_key_2.key"));
      default:
        throw new RuntimeException("Unknown privacy account");
    }
  }

  private static URL resolveResource(final String resource) {
    return PrivacyAccountResolver.class.getClassLoader().getResource(resource);
  }
}
