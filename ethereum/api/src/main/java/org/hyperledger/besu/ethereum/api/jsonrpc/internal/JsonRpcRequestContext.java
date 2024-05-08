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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.vertx.ext.auth.User;

/** The type Json rpc request context. */
public class JsonRpcRequestContext {

  private final JsonRpcRequest jsonRpcRequest;
  private final Optional<User> user;
  private final Supplier<Boolean> alive;

  /**
   * Instantiates a new Json rpc request context.
   *
   * @param jsonRpcRequest the json rpc request
   */
  public JsonRpcRequestContext(final JsonRpcRequest jsonRpcRequest) {
    this(jsonRpcRequest, () -> true);
  }

  /**
   * Instantiates a new Json rpc request context.
   *
   * @param jsonRpcRequest the json rpc request
   * @param alive the alive
   */
  public JsonRpcRequestContext(final JsonRpcRequest jsonRpcRequest, final Supplier<Boolean> alive) {
    this(jsonRpcRequest, Optional.empty(), alive);
  }

  /**
   * Instantiates a new Json rpc request context.
   *
   * @param jsonRpcRequest the json rpc request
   * @param user the user
   */
  public JsonRpcRequestContext(final JsonRpcRequest jsonRpcRequest, final User user) {
    this(jsonRpcRequest, Optional.of(user), () -> true);
  }

  /**
   * Instantiates a new Json rpc request context.
   *
   * @param jsonRpcRequest the json rpc request
   * @param user the user
   * @param alive the alive
   */
  public JsonRpcRequestContext(
      final JsonRpcRequest jsonRpcRequest, final User user, final Supplier<Boolean> alive) {
    this(jsonRpcRequest, Optional.of(user), alive);
  }

  /**
   * Instantiates a new Json rpc request context.
   *
   * @param jsonRpcRequest the json rpc request
   * @param user the user
   * @param alive the alive
   */
  public JsonRpcRequestContext(
      final JsonRpcRequest jsonRpcRequest,
      final Optional<User> user,
      final Supplier<Boolean> alive) {
    this.jsonRpcRequest = jsonRpcRequest;
    this.user = user;
    this.alive = alive;
  }

  /**
   * Gets request.
   *
   * @return the request
   */
  public JsonRpcRequest getRequest() {
    return jsonRpcRequest;
  }

  /**
   * Gets user.
   *
   * @return the user
   */
  public Optional<User> getUser() {
    return user;
  }

  /**
   * Gets required parameter.
   *
   * @param <T> the type parameter
   * @param index the index
   * @param paramClass the param class
   * @return the required parameter
   */
  public <T> T getRequiredParameter(final int index, final Class<T> paramClass) {
    return jsonRpcRequest.getRequiredParameter(index, paramClass);
  }

  /**
   * Gets optional parameter.
   *
   * @param <T> the type parameter
   * @param index the index
   * @param paramClass the param class
   * @return the optional parameter
   */
  public <T> Optional<T> getOptionalParameter(final int index, final Class<T> paramClass) {
    return jsonRpcRequest.getOptionalParameter(index, paramClass);
  }

  /**
   * Gets optional list.
   *
   * @param <T> the type parameter
   * @param index the index
   * @param listOf the list of
   * @return the optional list
   */
  public <T> Optional<List<T>> getOptionalList(final int index, final Class<T> listOf) {
    return jsonRpcRequest.getOptionalList(index, listOf);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JsonRpcRequestContext that = (JsonRpcRequestContext) o;
    return Objects.equals(jsonRpcRequest, that.jsonRpcRequest) && Objects.equals(user, that.user);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jsonRpcRequest, user);
  }

  /**
   * Is alive boolean.
   *
   * @return the boolean
   */
  public boolean isAlive() {
    return alive.get();
  }
}
