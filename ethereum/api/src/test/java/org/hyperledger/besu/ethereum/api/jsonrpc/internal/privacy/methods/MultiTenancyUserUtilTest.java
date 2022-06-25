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

import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.MultiTenancyUserUtil.privacyUserId;

import java.util.Optional;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.impl.UserImpl;
import org.junit.Test;

public class MultiTenancyUserUtilTest {

  @Test
  public void noPrivacyUserIdWhenNoUserProvided() {
    assertThat(privacyUserId(empty())).isEmpty();
  }

  @Test
  public void noEnclavePublicKeyWhenUserWithoutEnclavePublicKeyClaimProvided() {
    final JsonObject token = new JsonObject();
    final Optional<User> user = Optional.of(new UserImpl(token, new JsonObject()));

    assertThat(privacyUserId(user)).isEmpty();
  }

  @Test
  public void enclavePublicKeyKeyReturnedForUserWithEnclavePublicKeyClaim() {
    final JsonObject principle = new JsonObject();
    principle.put("privacyPublicKey", "ABC123");
    final Optional<User> user = Optional.of(new UserImpl(principle, new JsonObject()));

    assertThat(privacyUserId(user)).contains("ABC123");
  }
}
