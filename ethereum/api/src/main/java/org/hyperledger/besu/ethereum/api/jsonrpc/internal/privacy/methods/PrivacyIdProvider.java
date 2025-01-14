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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.MultiTenancyUserUtil.privacyUserId;

import org.hyperledger.besu.ethereum.core.PrivacyParameters;

import java.util.Optional;

import io.vertx.ext.auth.User;

@FunctionalInterface
@Deprecated(since = "24.12.0")
public interface PrivacyIdProvider {

  String getPrivacyUserId(Optional<User> user);

  static PrivacyIdProvider build(final PrivacyParameters privacyParameters) {
    if (privacyParameters.isMultiTenancyEnabled()) {
      return multiTenancyPrivacyUserIdProvider();
    }
    return defaultPrivacyUserIdProvider(privacyParameters);
  }

  private static PrivacyIdProvider multiTenancyPrivacyUserIdProvider() {
    return user ->
        privacyUserId(user)
            .orElseThrow(
                () -> new IllegalStateException("Request does not contain an authorization token"));
  }

  private static PrivacyIdProvider defaultPrivacyUserIdProvider(
      final PrivacyParameters privacyParameters) {
    return user -> privacyParameters.getPrivacyUserId();
  }
}
