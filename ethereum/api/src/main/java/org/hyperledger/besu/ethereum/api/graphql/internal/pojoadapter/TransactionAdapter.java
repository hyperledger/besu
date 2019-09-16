/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter;

import org.hyperledger.besu.ethereum.api.LogWithMetadata;
import org.hyperledger.besu.ethereum.api.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.api.graphql.internal.BlockchainQuery;
import org.hyperledger.besu.ethereum.api.graphql.internal.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;

@SuppressWarnings("unused") // reflected by GraphQL
public class TransactionAdapter extends AdapterBase {
  private final TransactionWithMetadata transactionWithMetadata;

  public TransactionAdapter(final TransactionWithMetadata transactionWithMetadata) {
    this.transactionWithMetadata = transactionWithMetadata;
  }

  public Optional<Hash> getHash() {
    return Optional.of(transactionWithMetadata.getTransaction().hash());
  }

  public Optional<Long> getNonce() {
    final long nonce = transactionWithMetadata.getTransaction().getNonce();
    return Optional.of(nonce);
  }

  public Optional<Integer> getIndex() {
    return transactionWithMetadata.getTransactionIndex();
  }

  public Optional<AccountAdapter> getFrom(final DataFetchingEnvironment environment) {
    final BlockchainQuery query = getBlockchainQuery(environment);
    final Optional<Long> txBlockNumber = transactionWithMetadata.getBlockNumber();
    final Optional<Long> bn = Optional.ofNullable(environment.getArgument("block"));
    if (!txBlockNumber.isPresent() && !bn.isPresent()) {
      return Optional.empty();
    }
    return query
        .getWorldState(bn.orElseGet(txBlockNumber::get))
        .map(
            mutableWorldState ->
                new AccountAdapter(
                    mutableWorldState.get(transactionWithMetadata.getTransaction().getSender())));
  }

  public Optional<AccountAdapter> getTo(final DataFetchingEnvironment environment) {
    final BlockchainQuery query = getBlockchainQuery(environment);
    final Optional<Long> txBlockNumber = transactionWithMetadata.getBlockNumber();
    final Optional<Long> bn = Optional.ofNullable(environment.getArgument("block"));
    if (!txBlockNumber.isPresent() && !bn.isPresent()) {
      return Optional.empty();
    }

    return query
        .getWorldState(bn.orElseGet(txBlockNumber::get))
        .flatMap(
            ws ->
                transactionWithMetadata
                    .getTransaction()
                    .getTo()
                    .map(addr -> new AccountAdapter(ws.get(addr))));
  }

  public Optional<UInt256> getValue() {
    return Optional.of(transactionWithMetadata.getTransaction().getValue().asUInt256());
  }

  public Optional<UInt256> getGasPrice() {
    return Optional.of(transactionWithMetadata.getTransaction().getGasPrice().asUInt256());
  }

  public Optional<Long> getGas() {
    return Optional.of(transactionWithMetadata.getTransaction().getGasLimit());
  }

  public Optional<BytesValue> getInputData() {
    return Optional.of(transactionWithMetadata.getTransaction().getPayload());
  }

  public Optional<NormalBlockAdapter> getBlock(final DataFetchingEnvironment environment) {
    return transactionWithMetadata
        .getBlockHash()
        .flatMap(blockHash -> getBlockchainQuery(environment).blockByHash(blockHash))
        .map(NormalBlockAdapter::new);
  }

  public Optional<Long> getStatus(final DataFetchingEnvironment environment) {
    return Optional.ofNullable(transactionWithMetadata.getTransaction())
        .map(Transaction::hash)
        .flatMap(rpt -> getBlockchainQuery(environment).transactionReceiptByTransactionHash(rpt))
        .map(TransactionReceiptWithMetadata::getReceipt)
        .flatMap(
            receipt ->
                receipt.getStatus() == -1
                    ? Optional.empty()
                    : Optional.of((long) receipt.getStatus()));
  }

  public Optional<Long> getGasUsed(final DataFetchingEnvironment environment) {
    final BlockchainQuery query = getBlockchainQuery(environment);
    final Optional<TransactionReceiptWithMetadata> rpt =
        query.transactionReceiptByTransactionHash(transactionWithMetadata.getTransaction().hash());
    return rpt.map(TransactionReceiptWithMetadata::getGasUsed);
  }

  public Optional<Long> getCumulativeGasUsed(final DataFetchingEnvironment environment) {
    final BlockchainQuery query = getBlockchainQuery(environment);
    final Optional<TransactionReceiptWithMetadata> rpt =
        query.transactionReceiptByTransactionHash(transactionWithMetadata.getTransaction().hash());
    if (rpt.isPresent()) {
      final TransactionReceipt receipt = rpt.get().getReceipt();
      return Optional.of(receipt.getCumulativeGasUsed());
    }
    return Optional.empty();
  }

  public Optional<AccountAdapter> getCreatedContract(final DataFetchingEnvironment environment) {
    final boolean contractCreated = transactionWithMetadata.getTransaction().isContractCreation();
    if (contractCreated) {
      final Optional<Address> addr = transactionWithMetadata.getTransaction().getTo();

      if (addr.isPresent()) {
        final BlockchainQuery query = getBlockchainQuery(environment);
        final Optional<Long> txBlockNumber = transactionWithMetadata.getBlockNumber();
        final Optional<Long> bn = Optional.ofNullable(environment.getArgument("block"));
        if (!txBlockNumber.isPresent() && !bn.isPresent()) {
          return Optional.empty();
        }
        final long blockNumber = bn.orElseGet(txBlockNumber::get);

        final Optional<WorldState> ws = query.getWorldState(blockNumber);
        if (ws.isPresent()) {
          return Optional.of(new AccountAdapter(ws.get().get(addr.get())));
        }
      }
    }
    return Optional.empty();
  }

  public List<LogAdapter> getLogs(final DataFetchingEnvironment environment) {
    final BlockchainQuery query = getBlockchainQuery(environment);
    final Hash hash = transactionWithMetadata.getTransaction().hash();
    final Optional<TransactionReceiptWithMetadata> tranRpt =
        query.transactionReceiptByTransactionHash(hash);
    final List<LogAdapter> results = new ArrayList<>();
    if (tranRpt.isPresent()) {
      final List<LogWithMetadata> logs =
          BlockchainQuery.generateLogWithMetadataForTransaction(
              tranRpt.get().getReceipt(),
              transactionWithMetadata.getBlockNumber().get(),
              transactionWithMetadata.getBlockHash().get(),
              hash,
              transactionWithMetadata.getTransactionIndex().get(),
              false);
      for (final LogWithMetadata log : logs) {
        results.add(new LogAdapter(log));
      }
    }
    return results;
  }
}
