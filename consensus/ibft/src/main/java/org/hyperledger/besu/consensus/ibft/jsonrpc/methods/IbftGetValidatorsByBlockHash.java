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
package org.hyperledger.besu.consensus.ibft.jsonrpc.methods;

import org.hyperledger.besu.consensus.common.BlockInterface;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IbftGetValidatorsByBlockHash implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(IbftGetValidatorsByBlockHash.class);

  private final Blockchain blockchain;
  private final BlockInterface blockInterface;

  public IbftGetValidatorsByBlockHash(
      final Blockchain blockchain, final BlockInterface blockInterface) {
    this.blockchain = blockchain;
    this.blockInterface = blockInterface;
  }

  @Override
  public String getName() {
    return RpcMethod.IBFT_GET_VALIDATORS_BY_BLOCK_HASH.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(), blockResult(requestContext));
  }

  private Object blockResult(final JsonRpcRequestContext request) {
    final Hash hash = request.getRequiredParameter(0, Hash.class);
    LOG.trace("Received RPC rpcName={} blockHash={}", getName(), hash);
    final Optional<BlockHeader> blockHeader = blockchain.getBlockHeader(hash);
    return blockHeader
        .map(
            header ->
                blockInterface.validatorsInBlock(header).stream()
                    .map(validator -> validator.toString())
                    .collect(Collectors.toList()))
        .orElse(null);
  }
}
