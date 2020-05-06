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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.MultiTenancyUserUtil.enclavePublicKey;

import org.hyperledger.besu.ethereum.core.PrivacyParameters;

import java.util.Optional;

import io.vertx.ext.auth.User;

@FunctionalInterface
public interface EnclavePublicKeyProvider {

  String getEnclaveKey(Optional<User> user);

  static EnclavePublicKeyProvider build(final PrivacyParameters privacyParameters) {
    return privacyParameters.isMultiTenancyEnabled()
        ? multiTenancyEnclavePublicKeyProvider()
        : defaultEnclavePublicKeyProvider(privacyParameters);
  }

  private static EnclavePublicKeyProvider multiTenancyEnclavePublicKeyProvider() {
    return user ->
        enclavePublicKey(user)
            .orElseThrow(
                () -> new IllegalStateException("Request does not contain an authorization token"));
  }

  private static EnclavePublicKeyProvider defaultEnclavePublicKeyProvider(
      final PrivacyParameters privacyParameters) {
    return user -> privacyParameters.getEnclavePublicKey();
  }
}
