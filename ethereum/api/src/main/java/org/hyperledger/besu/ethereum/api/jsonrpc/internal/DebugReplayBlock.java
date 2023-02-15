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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INTERNAL_ERROR;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.UNKNOWN_BLOCK;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.AbstractBlockParameterMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugReplayBlock extends AbstractBlockParameterMethod {
  private static final Logger LOG = LoggerFactory.getLogger(DebugReplayBlock.class);

  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;

  public DebugReplayBlock(
      final BlockchainQueries blockchain,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule) {
    super(blockchain);

    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_REPLAY_BLOCK.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    final Optional<Block> maybeBlock =
        getBlockchainQueries().getBlockchain().getBlockByNumber(blockNumber);

    if (maybeBlock.isEmpty()) {
      return new JsonRpcErrorResponse(request.getRequest().getId(), UNKNOWN_BLOCK);
    }

    // rewind to the block before the one we want to replay
    protocolContext.getBlockchain().rewindToBlock(blockNumber - 1);

    try {
      // replay block and persist it
      protocolSchedule
          .getByBlockNumber(blockNumber)
          .getBlockValidator()
          .validateAndProcessBlock(
              protocolContext,
              maybeBlock.get(),
              HeaderValidationMode.FULL,
              HeaderValidationMode.NONE,
              true);
    } catch (Exception e) {
      LOG.error(e.getMessage());
      return new JsonRpcErrorResponse(request.getRequest().getId(), INTERNAL_ERROR);
    }

    // set head to newly imported block
    protocolContext.getBlockchain().forwardToBlock(maybeBlock.get().getHeader());

    return JsonRpcSuccessResponse.SUCCESS_RESULT;
  }
}
