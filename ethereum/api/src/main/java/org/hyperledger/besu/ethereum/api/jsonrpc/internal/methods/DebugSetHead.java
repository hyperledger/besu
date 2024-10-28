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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.UNKNOWN_BLOCK;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;

public class DebugSetHead extends AbstractBlockParameterOrBlockHashMethod {
  private final ProtocolContext protocolContext;

  public DebugSetHead(final BlockchainQueries blockchain, final ProtocolContext protocolContext) {
    super(blockchain);

    this.protocolContext = protocolContext;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_SET_HEAD.getMethodName();
  }

  @Override
  protected BlockParameterOrBlockHash blockParameterOrBlockHash(
      final JsonRpcRequestContext requestContext) {
    try {
      return requestContext.getRequiredParameter(0, BlockParameterOrBlockHash.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block or block hash parameter (index 0)", RpcErrorType.INVALID_BLOCK_PARAMS, e);
    }
  }

  @Override
  protected Object resultByBlockHash(final JsonRpcRequestContext request, final Hash blockHash) {
    var blockchainQueries = getBlockchainQueries();
    Optional<BlockHeader> maybeBlockHeader = blockchainQueries.getBlockHeaderByHash(blockHash);
    Optional<Boolean> maybeMoveWorldstate = shouldMoveWorldstate(request);

    if (maybeBlockHeader.isEmpty()) {
      return new JsonRpcErrorResponse(request.getRequest().getId(), UNKNOWN_BLOCK);
    }

    protocolContext.getBlockchain().rewindToBlock(maybeBlockHeader.get().getBlockHash());

    // Optionally move the worldstate to the specified blockhash, if it is present
    if (maybeMoveWorldstate.orElse(Boolean.FALSE)) {
      var blockHeader = maybeBlockHeader.get();
      var archive = blockchainQueries.getWorldStateArchive();
      if (archive.isWorldStateAvailable(blockHeader.getStateRoot(), blockHeader.getBlockHash())) {
        // WARNING, this can be dangerous for a DiffBasedWorldstate if a concurrent
        //          process attempts to move or modify the head worldstate.
        //          Ensure no block processing is occuring when using this feature.
        //          No engine-api, block import, sync, or mining should be running.
        //          Even p2p transaction processing will be very expensive and should be disabled.
        //
        //          for Forest worldstates, this is essentially a no-op.
        archive.getMutable(blockHeader.getStateRoot(), blockHeader.getBlockHash());
      }
    }

    return JsonRpcSuccessResponse.SUCCESS_RESULT;
  }

  private Optional<Boolean> shouldMoveWorldstate(final JsonRpcRequestContext request) {
    try {
      return request.getOptionalParameter(1, Boolean.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid should move worldstate boolean parameter (index 1)",
          RpcErrorType.INVALID_PARAMS,
          e);
    }
  }
}
