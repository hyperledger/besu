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

import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions.TransactionInfo;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.PendingTransactionsStatisticsResult;

import java.util.Set;

public class TxPoolPantheonStatistics implements JsonRpcMethod {

  private final PendingTransactions pendingTransactions;

  public TxPoolPantheonStatistics(final PendingTransactions pendingTransactions) {
    this.pendingTransactions = pendingTransactions;
  }

  @Override
  public String getName() {
    return RpcMethod.TX_POOL_PANTHEON_STATISTICS.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    return new JsonRpcSuccessResponse(request.getId(), statistics());
  }

  private PendingTransactionsStatisticsResult statistics() {
    final Set<TransactionInfo> transactionInfo = pendingTransactions.getTransactionInfo();
    final long localCount =
        transactionInfo.stream().filter(TransactionInfo::isReceivedFromLocalSource).count();
    final long remoteCount = transactionInfo.size() - localCount;
    return new PendingTransactionsStatisticsResult(
        pendingTransactions.maxSize(), localCount, remoteCount);
  }
}
