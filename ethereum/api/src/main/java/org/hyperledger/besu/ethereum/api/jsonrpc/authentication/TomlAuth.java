/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import net.consensys.cava.toml.Toml;
import net.consensys.cava.toml.TomlParseResult;
import net.consensys.cava.toml.TomlTable;
import org.springframework.security.crypto.bcrypt.BCrypt;

public class TomlAuth implements AuthProvider {

  private final Vertx vertx;
  private final TomlAuthOptions options;

  public TomlAuth(final Vertx vertx, final TomlAuthOptions options) {
    this.vertx = vertx;
    this.options = options;
  }

  @Override
  public void authenticate(
      final JsonObject authInfo, final Handler<AsyncResult<User>> resultHandler) {
    final String username = authInfo.getString("username");
    if (username == null) {
      resultHandler.handle(Future.failedFuture("No username provided"));
      return;
    }

    final String password = authInfo.getString("password");
    if (password == null) {
      resultHandler.handle(Future.failedFuture("No password provided"));
      return;
    }

    readUser(
        username,
        rs -> {
          if (rs.succeeded()) {
            TomlUser user = rs.result();
            checkPasswordHash(
                password,
                user.getPassword(),
                rs2 -> {
                  if (rs2.succeeded()) {
                    resultHandler.handle(Future.succeededFuture(user));
                  } else {
                    resultHandler.handle(Future.failedFuture(rs2.cause()));
                  }
                });
          } else {
            resultHandler.handle(Future.failedFuture(rs.cause()));
          }
        });
  }

  private void readUser(final String username, final Handler<AsyncResult<TomlUser>> resultHandler) {
    vertx.executeBlocking(
        f -> {
          TomlParseResult parseResult;
          try {
            parseResult = Toml.parse(options.getTomlPath());
          } catch (IOException e) {
            f.fail(e);
            return;
          }

          final TomlTable userData = parseResult.getTableOrEmpty("Users." + username);
          if (userData.isEmpty()) {
            f.fail("User not found");
            return;
          }

          final TomlUser tomlUser = readTomlUserFromTable(username, userData);
          if ("".equals(tomlUser.getPassword())) {
            f.fail("No password set for user");
            return;
          }

          f.complete(tomlUser);
        },
        res -> {
          if (res.succeeded()) {
            resultHandler.handle(Future.succeededFuture((TomlUser) res.result()));
          } else {
            resultHandler.handle(Future.failedFuture(res.cause()));
          }
        });
  }

  private TomlUser readTomlUserFromTable(final String username, final TomlTable userData) {
    final String saltedAndHashedPassword = userData.getString("password", () -> "");
    final List<String> groups =
        userData.getArrayOrEmpty("groups").toList().stream()
            .map(Object::toString)
            .collect(Collectors.toList());
    final List<String> permissions =
        userData.getArrayOrEmpty("permissions").toList().stream()
            .map(Object::toString)
            .collect(Collectors.toList());
    final List<String> roles =
        userData.getArrayOrEmpty("roles").toList().stream()
            .map(Object::toString)
            .collect(Collectors.toList());

    return new TomlUser(username, saltedAndHashedPassword, groups, permissions, roles);
  }

  private void checkPasswordHash(
      final String password,
      final String passwordHash,
      final Handler<AsyncResult<Void>> resultHandler) {
    boolean passwordMatches = BCrypt.checkpw(password, passwordHash);
    if (passwordMatches) {
      resultHandler.handle(Future.succeededFuture());
    } else {
      resultHandler.handle(Future.failedFuture("Invalid password"));
    }
  }
}
