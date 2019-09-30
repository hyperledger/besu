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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.crosschain;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.crosschain.CrosschainProcessor;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;

/**
 * Request that the Crosschain Processor check with the Crosschain Coordination Contract whether the
 * contract should be unlocked or not.
 */
public class CrossCheckUnlock implements JsonRpcMethod {

  private final JsonRpcParameter parameters;

  private final CrosschainProcessor crosschainProcessor;

  public CrossCheckUnlock(
      final CrosschainProcessor crosschainProcessor, final JsonRpcParameter parameters) {
    this.parameters = parameters;
    this.crosschainProcessor = crosschainProcessor;
  }

  @Override
  public String getName() {
    return RpcMethod.CROSS_CHECK_UNLOCK.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 1) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }

    final Address address = this.parameters.required(request.getParams(), 0, Address.class);

    this.crosschainProcessor.checkUnlock(address);
    return new JsonRpcSuccessResponse(request.getId());
  }
}
