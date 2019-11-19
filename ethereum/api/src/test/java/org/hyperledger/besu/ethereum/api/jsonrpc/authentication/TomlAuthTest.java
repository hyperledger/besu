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

import java.net.URISyntaxException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class TomlAuthTest {

  private Vertx vertx;
  private JsonObject validAuthInfo;
  private TomlAuth tomlAuth;

  @Before
  public void before(final TestContext context) throws URISyntaxException {
    vertx = Vertx.vertx();
    tomlAuth =
        new TomlAuth(
            vertx, new TomlAuthOptions().setTomlPath(getTomlPath("authentication/auth.toml")));
    validAuthInfo = new JsonObject().put("username", "userA").put("password", "pegasys");
  }

  @Test
  public void authInfoWithoutUsernameShouldFailAuthentication(final TestContext context) {
    JsonObject authInfo = new JsonObject().put("password", "foo");

    tomlAuth.authenticate(
        authInfo,
        context.asyncAssertFailure(
            th -> context.assertEquals("No username provided", th.getMessage())));
  }

  @Test
  public void authInfoWithoutPasswordShouldFailAuthentication(final TestContext context) {
    JsonObject authInfo = new JsonObject().put("username", "foo");

    tomlAuth.authenticate(
        authInfo,
        context.asyncAssertFailure(
            th -> context.assertEquals("No password provided", th.getMessage())));
  }

  @Test
  public void parseFailureWithIOExceptionShouldFailAuthentication(final TestContext context) {
    tomlAuth = new TomlAuth(vertx, new TomlAuthOptions().setTomlPath("invalid_path"));

    tomlAuth.authenticate(
        validAuthInfo,
        context.asyncAssertFailure(
            th -> {
              context.assertEquals(th.getClass(), NoSuchFileException.class);
            }));
  }

  @Test
  public void authInfoWithAbsentUserShouldFailAuthentication(final TestContext context) {
    JsonObject authInfo = new JsonObject().put("username", "foo").put("password", "foo");

    tomlAuth.authenticate(
        authInfo,
        context.asyncAssertFailure(th -> context.assertEquals("User not found", th.getMessage())));
  }

  @Test
  public void userWithoutPasswordSetShouldFailAuthentication(final TestContext context) {
    JsonObject authInfo = new JsonObject().put("username", "noPasswordUser").put("password", "foo");

    tomlAuth.authenticate(
        authInfo,
        context.asyncAssertFailure(
            th -> context.assertEquals("No password set for user", th.getMessage())));
  }

  @Test
  public void passwordMismatchShouldFailAuthentication(final TestContext context) {
    JsonObject authInfo = new JsonObject().put("username", "userA").put("password", "foo");

    tomlAuth.authenticate(
        authInfo,
        context.asyncAssertFailure(
            th -> context.assertEquals("Invalid password", th.getMessage())));
  }

  @Test
  public void validPasswordWithAllValuesShouldAuthenticateAndCreateUserSuccessfully(
      final TestContext context) {
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
        authInfo,
        context.asyncAssertSuccess(
            res -> context.assertEquals(expectedPrincipal, res.principal())));
  }

  @Test
  public void validPasswordWithOptionalValuesShouldAuthenticateAndCreateUserSuccessfully(
      final TestContext context) {
    JsonObject expectedPrincipal =
        new JsonObject()
            .put("username", "userB")
            .put("password", "$2a$10$l3GA7K8g6rJ/Yv.YFSygCuI9byngpEzxgWS9qEg5emYDZomQW7fGC")
            .put("groups", new JsonArray())
            .put("permissions", new JsonArray().add("net:*"))
            .put("roles", new JsonArray());

    JsonObject authInfo = new JsonObject().put("username", "userB").put("password", "pegasys");

    tomlAuth.authenticate(
        authInfo,
        context.asyncAssertSuccess(
            res -> context.assertEquals(expectedPrincipal, res.principal())));
  }

  private String getTomlPath(final String tomlFileName) throws URISyntaxException {
    return Paths.get(ClassLoader.getSystemResource(tomlFileName).toURI())
        .toAbsolutePath()
        .toString();
  }
}
