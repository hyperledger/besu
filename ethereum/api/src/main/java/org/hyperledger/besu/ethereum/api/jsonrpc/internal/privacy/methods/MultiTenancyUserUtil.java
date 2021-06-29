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

import java.util.Optional;

import io.vertx.ext.auth.User;

public class MultiTenancyUserUtil {
  private static final String PRIVACY_USER_ID_CLAIM = "privacyUserId";
  private static final String ENCLAVE_PRIVACY_PUBLIC_KEY_CLAIM = "privacyPublicKey";

  public static Optional<String> privacyUserId(final Optional<User> user) {
    return user.map(
        u -> {
          final String id = u.principal().getString(PRIVACY_USER_ID_CLAIM);
          return id != null ? id : u.principal().getString(ENCLAVE_PRIVACY_PUBLIC_KEY_CLAIM);
        });
  }
}
