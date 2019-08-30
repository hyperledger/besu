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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.privacy.methods.eea;

import static org.apache.logging.log4j.LogManager.getLogger;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.Quantity;

import org.apache.logging.log4j.Logger;

public class EeaGetTransactionCount implements JsonRpcMethod {

  private static final Logger LOG = getLogger();

  private final JsonRpcParameter parameters;
  private final EeaPrivateNonceProvider nonceProvider;

  public EeaGetTransactionCount(
      final JsonRpcParameter parameters, final EeaPrivateNonceProvider nonceProvider) {
    this.parameters = parameters;
    this.nonceProvider = nonceProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.EEA_GET_TRANSACTION_COUNT.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 3) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }

    final Address address = parameters.required(request.getParams(), 0, Address.class);
    final String privateFrom = parameters.required(request.getParams(), 1, String.class);
    final String[] privateFor = parameters.required(request.getParams(), 2, String[].class);

    try {
      final long nonce = nonceProvider.determineNonce(privateFrom, privateFor, address);
      return new JsonRpcSuccessResponse(request.getId(), Quantity.create(nonce));
    } catch (final Exception e) {
      LOG.error(e.getMessage(), e);
      return new JsonRpcErrorResponse(
          request.getId(), JsonRpcError.GET_PRIVATE_TRANSACTION_NONCE_ERROR);
    }
  }
}
