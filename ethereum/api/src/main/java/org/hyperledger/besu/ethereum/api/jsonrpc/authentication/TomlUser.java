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

import java.util.List;
import java.util.Optional;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.Authorization;
import io.vertx.ext.auth.authorization.Authorizations;

/** The type Toml user. */
public class TomlUser implements User {

  private final String username;
  private final String password;
  private final List<String> groups;
  private final List<String> permissions;
  private final List<String> roles;
  private final Optional<String> privacyPublicKey;

  /**
   * Instantiates a new Toml user.
   *
   * @param username the username
   * @param password the password
   * @param groups the groups
   * @param permissions the permissions
   * @param roles the roles
   * @param privacyPublicKey the privacy public key
   */
  TomlUser(
      final String username,
      final String password,
      final List<String> groups,
      final List<String> permissions,
      final List<String> roles,
      final Optional<String> privacyPublicKey) {
    this.username = username;
    this.password = password;
    this.groups = groups;
    this.permissions = permissions;
    this.roles = roles;
    this.privacyPublicKey = privacyPublicKey;
  }

  @Override
  public JsonObject attributes() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public boolean expired() {
    return false;
  }

  @Override
  public boolean expired(final int leeway) {
    return false;
  }

  @Override
  public boolean containsKey(final String key) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public Authorizations authorizations() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public User isAuthorized(
      final Authorization authority, final Handler<AsyncResult<Boolean>> resultHandler) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public JsonObject principal() {
    final JsonObject principle =
        new JsonObject()
            .put("username", username)
            .put("password", password)
            .put("groups", groups)
            .put("permissions", permissions)
            .put("roles", roles);
    privacyPublicKey.ifPresent(pk -> principle.put("privacyPublicKey", pk));
    return principle;
  }

  @Override
  public void setAuthProvider(final AuthProvider authProvider) {
    // we only use Toml for authentication
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public User merge(final User other) {
    throw new UnsupportedOperationException("Not implemented");
  }

  /**
   * Do is permitted.
   *
   * @param permission the permission
   * @param resultHandler the result handler
   */
  protected void doIsPermitted(
      final String permission, final Handler<AsyncResult<Boolean>> resultHandler) {
    // we only use Toml for authentication
    throw new UnsupportedOperationException("Not implemented");
  }

  /**
   * Gets username.
   *
   * @return the username
   */
  public String getUsername() {
    return username;
  }

  /**
   * Gets password.
   *
   * @return the password
   */
  public String getPassword() {
    return password;
  }

  /**
   * Gets groups.
   *
   * @return the groups
   */
  public List<String> getGroups() {
    return groups;
  }

  /**
   * Gets permissions.
   *
   * @return the permissions
   */
  public List<String> getPermissions() {
    return permissions;
  }

  /**
   * Gets roles.
   *
   * @return the roles
   */
  public List<String> getRoles() {
    return roles;
  }

  /**
   * Gets privacy public key.
   *
   * @return the privacy public key
   */
  public Optional<String> getPrivacyPublicKey() {
    return privacyPublicKey;
  }
}
