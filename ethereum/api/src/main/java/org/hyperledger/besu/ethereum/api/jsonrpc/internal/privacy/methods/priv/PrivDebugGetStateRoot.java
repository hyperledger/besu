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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import static org.apache.logging.log4j.LogManager.getLogger;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.FIND_PRIVACY_GROUP_ERROR;

import org.hyperledger.besu.enclave.types.PrivacyGroup;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AbstractBlockParameterMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.EnclavePublicKeyProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.Collections;

import org.apache.logging.log4j.Logger;

public class PrivDebugGetStateRoot extends AbstractBlockParameterMethod {

  private static final Logger LOG = getLogger();

  private final EnclavePublicKeyProvider enclavePublicKeyProvider;
  private final PrivacyController privacyController;

  public PrivDebugGetStateRoot(
      final BlockchainQueries blockchainQueries,
      final EnclavePublicKeyProvider enclavePublicKeyProvider,
      final PrivacyController privacyController) {
    super(blockchainQueries);
    this.enclavePublicKeyProvider = enclavePublicKeyProvider;
    this.privacyController = privacyController;
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_DEBUG_GET_STATE_ROOT.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(1, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext requestContext, final long blockNumber) {
    final String privacyGroupId = requestContext.getRequiredParameter(0, String.class);
    final String enclavePublicKey =
        enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser());
    LOG.trace("Executing {}", this::getName);

    final PrivacyGroup[] privacyGroups =
        privacyController.findPrivacyGroup(
            Collections.singletonList(privacyGroupId),
            enclavePublicKeyProvider.getEnclaveKey(requestContext.getUser()));

    if (privacyGroups.length == 0) {
      LOG.error("Failed to fetch privacy group");
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), FIND_PRIVACY_GROUP_ERROR);
    }

    return privacyController
        .getStateRootByBlockNumber(privacyGroupId, enclavePublicKey, blockNumber)
        .<JsonRpcResponse>map(
            stateRootHash ->
                new JsonRpcSuccessResponse(requestContext.getRequest().getId(), stateRootHash))
        .orElse(
            new JsonRpcErrorResponse(
                requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS));
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return (JsonRpcResponse) findResultByParamType(requestContext);
  }
}
