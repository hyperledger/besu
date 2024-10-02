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
package org.hyperledger.besu.consensus.clique.jsonrpc.methods;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** The Clique get signers at hash. */
public class CliqueGetSignersAtHash implements JsonRpcMethod {
  private final BlockchainQueries blockchainQueries;
  private final ValidatorProvider validatorProvider;

  /**
   * Instantiates a new Clique get signers at hash.
   *
   * @param blockchainQueries the blockchain queries
   * @param validatorProvider the validator provider
   */
  public CliqueGetSignersAtHash(
      final BlockchainQueries blockchainQueries, final ValidatorProvider validatorProvider) {
    this.blockchainQueries = blockchainQueries;
    this.validatorProvider = validatorProvider;
  }

  @Override
  public String getName() {
    return RpcMethod.CLIQUE_GET_SIGNERS_AT_HASH.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final Optional<BlockHeader> blockHeader = determineBlockHeader(requestContext);
    return blockHeader
        .map(validatorProvider::getValidatorsAfterBlock)
        .map(addresses -> addresses.stream().map(Objects::toString).collect(Collectors.toList()))
        .<JsonRpcResponse>map(
            addresses -> new JsonRpcSuccessResponse(requestContext.getRequest().getId(), addresses))
        .orElse(
            new JsonRpcErrorResponse(
                requestContext.getRequest().getId(), RpcErrorType.INTERNAL_ERROR));
  }

  private Optional<BlockHeader> determineBlockHeader(final JsonRpcRequestContext request) {
    final Hash hash;
    try {
      hash = request.getRequiredParameter(0, Hash.class);
    } catch (JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid block hash parameter (index 0)", RpcErrorType.INVALID_BLOCK_HASH_PARAMS, e);
    }
    return blockchainQueries.blockByHash(hash).map(BlockWithMetadata::getHeader);
  }
}
