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
package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.list;

import java.util.Optional;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

public class TomlUserTest {

  @Test
  public void createsPrincipleWithAllValues() {
    final TomlUser tomlUser =
        new TomlUser(
            "user",
            "password",
            list("admin"),
            list("eth:*", "perm:*"),
            list("net"),
            Optional.of("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="));

    final JsonObject principal = tomlUser.principal();
    assertThat(principal.getString("username")).isEqualTo("user");
    assertThat(principal.getString("password")).isEqualTo("password");
    assertThat(principal.getJsonArray("groups")).isEqualTo(new JsonArray(list("admin")));
    assertThat(principal.getJsonArray("permissions"))
        .isEqualTo(new JsonArray(list("eth:*", "perm:*")));
    assertThat(principal.getJsonArray("roles")).isEqualTo(new JsonArray(list("net")));
    assertThat(principal.getString("privacyPublicKey"))
        .isEqualTo("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");
  }

  @Test
  public void createsPrincipalWithOnlyRequiredValues() {
    final TomlUser tomlUser =
        new TomlUser("user", "password", list(), list(), list(), Optional.empty());

    final JsonObject principal = tomlUser.principal();
    assertThat(principal.getString("username")).isEqualTo("user");
    assertThat(principal.getString("password")).isEqualTo("password");
    assertThat(principal.getJsonArray("groups")).isEqualTo(new JsonArray());
    assertThat(principal.getJsonArray("permissions")).isEqualTo(new JsonArray());
    assertThat(principal.getJsonArray("roles")).isEqualTo(new JsonArray());
    assertThat(principal.containsKey("privacyPublicKey")).isFalse();
  }
}
