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
package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ApiGroupJsonRpcMethods implements JsonRpcMethods {

  @Override
  public Map<String, JsonRpcMethod> create(final Collection<String> apis) {
    return apis.contains(getApiGroup()) ? create() : Collections.emptyMap();
  }

  protected abstract String getApiGroup();

  protected abstract Map<String, JsonRpcMethod> create();

  protected Map<String, JsonRpcMethod> mapOf(final JsonRpcMethod... methods) {
    return Arrays.stream(methods)
        .collect(Collectors.toMap(JsonRpcMethod::getName, method -> method));
  }

  protected Map<String, JsonRpcMethod> mapOf(final List<JsonRpcMethod> methods) {
    return methods.stream().collect(Collectors.toMap(JsonRpcMethod::getName, method -> method));
  }
}
