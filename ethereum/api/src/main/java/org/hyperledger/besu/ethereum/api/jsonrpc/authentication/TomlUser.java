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

import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;

public class TomlUser extends AbstractUser {

  private final String username;
  private final String password;
  private final List<String> groups;
  private final List<String> permissions;
  private final List<String> roles;

  TomlUser(
      final String username,
      final String password,
      final List<String> groups,
      final List<String> permissions,
      final List<String> roles) {
    this.username = username;
    this.password = password;
    this.groups = groups;
    this.permissions = permissions;
    this.roles = roles;
  }

  @Override
  public JsonObject principal() {
    return new JsonObject()
        .put("username", username)
        .put("password", password)
        .put("groups", groups)
        .put("permissions", permissions)
        .put("roles", roles);
  }

  @Override
  public void setAuthProvider(final AuthProvider authProvider) {
    // we only use Toml for authentication
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  protected void doIsPermitted(
      final String permission, final Handler<AsyncResult<Boolean>> resultHandler) {
    // we only use Toml for authentication
    throw new UnsupportedOperationException("Not implemented");
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public List<String> getGroups() {
    return groups;
  }

  public List<String> getPermissions() {
    return permissions;
  }

  public List<String> getRoles() {
    return roles;
  }
}
