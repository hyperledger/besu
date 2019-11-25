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
package org.hyperledger.besu.crosschain.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.crosschain.core.CrosschainController;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Address;

import java.math.BigInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Sets the address of the key generation contract to use. */
public class CrossRemoveCoordinationContract implements JsonRpcMethod {

  private static final Logger LOG = LogManager.getLogger();

  private final CrosschainController crosschainController;
  private final JsonRpcParameter parameters;

  public CrossRemoveCoordinationContract(
      final CrosschainController crosschainController, final JsonRpcParameter parameters) {
    this.crosschainController = crosschainController;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.CROSS_REMOVE_COORDINAITON_CONTRACT.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 2) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
    final BigInteger blockchainId =
        this.parameters.required(request.getParams(), 0, BigInteger.class);
    final Address address = this.parameters.required(request.getParams(), 1, Address.class);

    LOG.trace("JSON RPC {}: Blockchain Id: {}, Address: {}", getName(), blockchainId, address);

    this.crosschainController.removeCoordinaitonContract(blockchainId, address);
    return new JsonRpcSuccessResponse(request.getId());
  }
}
