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
package org.hyperledger.besu.ethereum.api.jsonrpc.context;

import java.util.function.Supplier;

import io.vertx.ext.web.RoutingContext;

public enum ContextKey {
  REQUEST_BODY_AS_JSON_OBJECT,
  REQUEST_BODY_AS_JSON_ARRAY,
  AUTHENTICATED_USER;

  public <T> T extractFrom(final RoutingContext ctx, final Supplier<T> defaultSupplier) {
    final T value = ctx.get(this.name());
    return value != null ? value : defaultSupplier.get();
  }
}
