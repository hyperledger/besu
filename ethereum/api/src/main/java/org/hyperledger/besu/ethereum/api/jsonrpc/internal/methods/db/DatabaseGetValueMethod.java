/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.db;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public class DatabaseGetValueMethod implements JsonRpcMethod {

  private final StorageProvider storageProvider;

  public DatabaseGetValueMethod(final StorageProvider storageProvider) {
    this.storageProvider = storageProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.DATABASE_GET_VALUE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext request) {
    final String segment = request.getRequiredParameter(0, String.class);
    final String key = request.getRequiredParameter(1, String.class);
    final KeyValueStorage keyValueStorage =
        storageProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.valueOf(segment));
    final Optional<byte[]> value = keyValueStorage.get(Bytes.fromHexString(key).toArrayUnsafe());
    if (value.isPresent()) {
      return new JsonRpcSuccessResponse(request.getRequest().getId(), value.map(Bytes::of).map(Bytes::toHexString));
    } else {
      return new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INTERNAL_ERROR);
    }
  }
}
