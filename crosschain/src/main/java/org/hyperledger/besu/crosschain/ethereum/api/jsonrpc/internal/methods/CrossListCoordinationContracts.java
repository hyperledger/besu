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
import org.hyperledger.besu.crosschain.core.keys.CoordinationContractInformation;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Returns the list of nodes that hold secret shares and who can participate in threshold signing.
 * During a key generation, this will be the set of nodes still active in the key generation
 * process.
 */
public class CrossListCoordinationContracts implements JsonRpcMethod {
  private static final Logger LOG = LogManager.getLogger();
  private final CrosschainController crosschainController;

  public CrossListCoordinationContracts(final CrosschainController crosschainController) {
    this.crosschainController = crosschainController;
  }

  @Override
  public String getName() {
    return RpcMethod.CROSS_LIST_COORDINAITON_CONTRACTS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 0) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }

    Collection<CoordinationContractInformation> info =
        this.crosschainController.listCoordinationContracts();
    LOG.trace("JSON RPC {}: Size: {}", getName(), info.size());
    return new JsonRpcSuccessResponse(request.getId(), info);
  }
}
