/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.Quantity;

import java.util.OptionalLong;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;

public class EthGetTransactionCount extends AbstractBlockParameterMethod {

  private final Supplier<PendingTransactions> pendingTransactions;
  private final boolean resultAsDecimal;

  public EthGetTransactionCount(
      final BlockchainQueries blockchain,
      final PendingTransactions pendingTransactions,
      final JsonRpcParameter parameters) {
    this(
        Suppliers.ofInstance(blockchain),
        Suppliers.ofInstance(pendingTransactions),
        parameters,
        false);
  }

  public EthGetTransactionCount(
      final Supplier<BlockchainQueries> blockchain,
      final Supplier<PendingTransactions> pendingTransactions,
      final JsonRpcParameter parameters,
      final boolean resultAsDecimal) {
    super(blockchain, parameters);
    this.pendingTransactions = pendingTransactions;
    this.resultAsDecimal = resultAsDecimal;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_TRANSACTION_COUNT.getMethodName();
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return getParameters().required(request.getParams(), 1, BlockParameter.class);
  }

  @Override
  protected Object pendingResult(final JsonRpcRequest request) {
    final Address address = getParameters().required(request.getParams(), 0, Address.class);
    final OptionalLong pendingNonce = pendingTransactions.get().getNextNonceForSender(address);
    if (pendingNonce.isPresent()) {
      return Quantity.create(pendingNonce.getAsLong());
    } else {
      return latestResult(request);
    }
  }

  @Override
  protected String resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    final Address address = getParameters().required(request.getParams(), 0, Address.class);
    if (blockNumber > getBlockchainQueries().headBlockNumber()) {
      return null;
    }
    final long transactionCount = getBlockchainQueries().getTransactionCount(address, blockNumber);
    return resultAsDecimal ? Long.toString(transactionCount) : Quantity.create(transactionCount);
  }
}
