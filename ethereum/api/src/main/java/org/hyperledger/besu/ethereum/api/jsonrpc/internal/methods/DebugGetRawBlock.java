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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.rlp.RLP;

import com.google.common.base.Suppliers;

public class DebugGetRawBlock extends AbstractBlockParameterMethod {

  public DebugGetRawBlock(final BlockchainQueries blockchain) {
    super(Suppliers.ofInstance(blockchain));
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_GET_RAW_BLOCK.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {

    return getBlockchainQueries()
        .getBlockchain()
        .getBlockByNumber(blockNumber)
        .<Object>map(block -> RLP.encode(block::writeTo).toString())
        .orElseGet(
            () ->
                new JsonRpcErrorResponse(
                    request.getRequest().getId(), RpcErrorType.BLOCK_NOT_FOUND));
  }
}
