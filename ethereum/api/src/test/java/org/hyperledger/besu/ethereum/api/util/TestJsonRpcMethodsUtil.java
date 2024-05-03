/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.util;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Optional;

public class TestJsonRpcMethodsUtil {

  public static JsonRpcMethod optionalEmptyResponse() {
    return new JsonRpcMethod() {
      @Override
      public String getName() {
        return "temp_optionalEmptyResponse";
      }

      @Override
      public JsonRpcResponse response(final JsonRpcRequestContext request) {
        return new JsonRpcSuccessResponse(request.getRequest().getId(), Optional.empty());
      }
    };
  }

  public static JsonRpcMethod optionalResponseWithValue(final Object value) {
    return new JsonRpcMethod() {
      @Override
      public String getName() {
        return "temp_optionalWithValueResponse";
      }

      @Override
      public JsonRpcResponse response(final JsonRpcRequestContext request) {
        return new JsonRpcSuccessResponse(request.getRequest().getId(), Optional.of(value));
      }
    };
  }
}
