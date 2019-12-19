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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class EthGetBlockByNumber extends AbstractBlockParameterMethod {

  private final BlockResultFactory blockResult;
  private final boolean includeCoinbase;

  public EthGetBlockByNumber(
      final BlockchainQueries blockchain, final BlockResultFactory blockResult) {
    this(Suppliers.ofInstance(blockchain), blockResult, false);
  }

  public EthGetBlockByNumber(
      final Supplier<BlockchainQueries> blockchain,
      final BlockResultFactory blockResult,
      final boolean includeCoinbase) {
    super(blockchain);
    this.blockResult = blockResult;
    this.includeCoinbase = includeCoinbase;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_BLOCK_BY_NUMBER.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(0, BlockParameter.class);
  }

  @Override
  protected Object resultByBlockNumber(
      final JsonRpcRequestContext request, final long blockNumber) {
    if (isCompleteTransactions(request)) {
      return transactionComplete(blockNumber);
    }

    return transactionHash(blockNumber);
  }

  private BlockResult transactionComplete(final long blockNumber) {
    return getBlockchainQueries()
        .blockByNumber(blockNumber)
        .map(tx -> blockResult.transactionComplete(tx, includeCoinbase))
        .orElse(null);
  }

  private BlockResult transactionHash(final long blockNumber) {
    return getBlockchainQueries()
        .blockByNumberWithTxHashes(blockNumber)
        .map(tx -> blockResult.transactionHash(tx, includeCoinbase))
        .orElse(null);
  }

  private boolean isCompleteTransactions(final JsonRpcRequestContext request) {
    return request.getRequiredParameter(1, Boolean.class);
  }
}
