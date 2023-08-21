/*
 * Copyright Hyperledger Besu contributors
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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameterOrBlockHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockReceiptsResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptRootResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptStatusResult;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.TransactionReceiptType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.base.Suppliers;

public class EthGetBlockReceipts extends AbstractBlockParameterOrBlockHashMethod {

  private final ProtocolSchedule protocolSchedule;

  public EthGetBlockReceipts(
      final BlockchainQueries blockchain, final ProtocolSchedule protocolSchedule) {
    super(Suppliers.ofInstance(blockchain));
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_GET_BLOCK_RECEIPTS.getMethodName();
  }

  @Override
  protected BlockParameterOrBlockHash blockParameterOrBlockHash(
      final JsonRpcRequestContext request) {
    return request.getRequiredParameter(0, BlockParameterOrBlockHash.class);
  }

  @Override
  protected Object resultByBlockHash(final JsonRpcRequestContext request, final Hash blockHash) {
    return getBlockReceiptsResult(blockHash);
  }

  /*
   * For a given transaction, get its receipt and if it exists, wrap in a transaction receipt of the correct type
   */
  private Optional<TransactionReceiptResult> txReceipt(final TransactionWithMetadata tx) {
    Optional<TransactionReceiptWithMetadata> receipt =
        blockchainQueries
            .get()
            .transactionReceiptByTransactionHash(tx.getTransaction().getHash(), protocolSchedule);
    if (receipt.isPresent()) {
      if (receipt.get().getReceipt().getTransactionReceiptType() == TransactionReceiptType.ROOT) {
        return Optional.of(new TransactionReceiptRootResult(receipt.get()));
      } else {
        return Optional.of(new TransactionReceiptStatusResult(receipt.get()));
      }
    }
    return Optional.empty();
  }

  private BlockReceiptsResult getBlockReceiptsResult(final Hash blockHash) {
    final List<TransactionReceiptResult> receiptList =
        blockchainQueries
            .get()
            .blockByHash(blockHash)
            .map(
                block ->
                    block.getTransactions().stream()
                        .map(tx -> txReceipt(tx).get())
                        .collect(Collectors.toList()))
            .orElse(new ArrayList<>());

    return new BlockReceiptsResult(receiptList);
  }
}
