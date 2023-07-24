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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.UNKNOWN_BLOCK;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import java.util.Optional;

public class DebugSetHead extends AbstractBlockParameterMethod {
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
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    final Optional<Hash> maybeBlockHash = getBlockchainQueries().getBlockHashByNumber(blockNumber);

    if (maybeBlockHash.isEmpty()) {
      return new JsonRpcErrorResponse(request.getRequest().getId(), UNKNOWN_BLOCK);
    }

    protocolContext.getBlockchain().rewindToBlock(maybeBlockHash.get());

    return JsonRpcSuccessResponse.SUCCESS_RESULT;
  }
}
