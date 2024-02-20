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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BadBlockResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;

import java.util.List;
import java.util.stream.Collectors;

public class DebugGetBadBlocks implements JsonRpcMethod {

  private final ProtocolContext protocolContext;
  private final BlockResultFactory blockResultFactory;

  public DebugGetBadBlocks(
      final ProtocolContext protocolContext, final BlockResultFactory blockResultFactory) {
    this.protocolContext = protocolContext;
    this.blockResultFactory = blockResultFactory;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_GET_BAD_BLOCKS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    final List<BadBlockResult> response =
        protocolContext.getBadBlockManager().getBadBlocks().stream()
            .map(block -> BadBlockResult.from(blockResultFactory.transactionComplete(block), block))
            .collect(Collectors.toList());
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), response);
  }
}
