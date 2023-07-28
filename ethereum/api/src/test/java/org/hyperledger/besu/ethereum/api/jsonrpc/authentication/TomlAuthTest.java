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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class TomlAuthTest {

  private Vertx vertx;
  private VertxTestContext testContext;
  private JsonObject validAuthInfo;
  private TomlAuth tomlAuth;

  @BeforeEach
  public void before() throws URISyntaxException {
    vertx = Vertx.vertx();
    testContext = new VertxTestContext();

    tomlAuth =
        new TomlAuth(
            vertx, new TomlAuthOptions().setTomlPath(getTomlPath("authentication/auth.toml")));
    validAuthInfo = new JsonObject().put("username", "userA").put("password", "pegasys");
  }

  @Test
  public void authInfoWithoutUsernameShouldFailAuthentication() {
    JsonObject authInfo = new JsonObject().put("password", "foo");

    tomlAuth.authenticate(
        authInfo, testContext.failing(th -> assertEquals("No username provided", th.getMessage())));
  }

  @Test
  public void authInfoWithoutPasswordShouldFailAuthentication() {
    JsonObject authInfo = new JsonObject().put("username", "foo");

    tomlAuth.authenticate(
        authInfo, testContext.failing(th -> assertEquals("No password provided", th.getMessage())));
  }

  @Test
  public void parseFailureWithIOExceptionShouldFailAuthentication() {
    tomlAuth = new TomlAuth(vertx, new TomlAuthOptions().setTomlPath("invalid_path"));

    tomlAuth.authenticate(
        validAuthInfo,
        testContext.failing(th -> assertEquals(th.getClass(), NoSuchFileException.class)));
  }

  @Test
  public void authInfoWithAbsentUserShouldFailAuthentication() {
    JsonObject authInfo = new JsonObject().put("username", "foo").put("password", "foo");

    tomlAuth.authenticate(
        authInfo, testContext.failing(th -> assertEquals("User not found", th.getMessage())));
  }

  @Test
  public void userWithoutPasswordSetShouldFailAuthentication() {
    JsonObject authInfo = new JsonObject().put("username", "noPasswordUser").put("password", "foo");

    tomlAuth.authenticate(
        authInfo,
        testContext.failing(th -> assertEquals("No password set for user", th.getMessage())));
  }

  @Test
  public void passwordMismatchShouldFailAuthentication() {
    JsonObject authInfo = new JsonObject().put("username", "userA").put("password", "foo");

    tomlAuth.authenticate(
        authInfo, testContext.failing(th -> assertEquals("Invalid password", th.getMessage())));
  }

  @Test
  public void validPasswordWithAllValuesShouldAuthenticateAndCreateUserSuccessfully() {
    JsonObject expectedPrincipal =
        new JsonObject()
            .put("username", "userA")
            .put("password", "$2a$10$l3GA7K8g6rJ/Yv.YFSygCuI9byngpEzxgWS9qEg5emYDZomQW7fGC")
            .put("groups", new JsonArray().add("admin"))
            .put("permissions", new JsonArray().add("eth:*").add("perm:*"))
            .put("roles", new JsonArray().add("net"))
            .put("privacyPublicKey", "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

    JsonObject authInfo = new JsonObject().put("username", "userA").put("password", "pegasys");

    tomlAuth.authenticate(
        authInfo, testContext.succeeding(res -> assertEquals(expectedPrincipal, res.principal())));
  }

  @Test
  public void validPasswordWithOptionalValuesShouldAuthenticateAndCreateUserSuccessfully() {
    JsonObject expectedPrincipal =
        new JsonObject()
            .put("username", "userB")
            .put("password", "$2a$10$l3GA7K8g6rJ/Yv.YFSygCuI9byngpEzxgWS9qEg5emYDZomQW7fGC")
            .put("groups", new JsonArray())
            .put("permissions", new JsonArray().add("net:*"))
            .put("roles", new JsonArray());

    JsonObject authInfo = new JsonObject().put("username", "userB").put("password", "pegasys");

    tomlAuth.authenticate(
        authInfo, testContext.succeeding(res -> assertEquals(expectedPrincipal, res.principal())));
  }

  private String getTomlPath(final String tomlFileName) throws URISyntaxException {
    return Paths.get(ClassLoader.getSystemResource(tomlFileName).toURI())
        .toAbsolutePath()
        .toString();
  }
}
