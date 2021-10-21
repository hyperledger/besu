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
package org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter;

import org.hyperledger.besu.config.GoQuorumOptions;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;

@SuppressWarnings("unused") // reflected by GraphQL
public class TransactionAdapter extends AdapterBase {
  private final TransactionWithMetadata transactionWithMetadata;

  public TransactionAdapter(final TransactionWithMetadata transactionWithMetadata) {
    this.transactionWithMetadata = transactionWithMetadata;
  }

  public Optional<Hash> getHash() {
    return Optional.of(transactionWithMetadata.getTransaction().getHash());
  }

  public Optional<Long> getNonce() {
    final long nonce = transactionWithMetadata.getTransaction().getNonce();
    return Optional.of(nonce);
  }

  public Optional<Integer> getIndex() {
    return transactionWithMetadata.getTransactionIndex();
  }

  public Optional<AccountAdapter> getFrom(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
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
    final BlockchainQueries query = getBlockchainQueries(environment);
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

  public Optional<Wei> getValue() {
    return Optional.of(transactionWithMetadata.getTransaction().getValue());
  }

  public Optional<Wei> getGasPrice() {
    return transactionWithMetadata.getTransaction().getGasPrice();
  }

  public Optional<Long> getGas() {
    return Optional.of(transactionWithMetadata.getTransaction().getGasLimit());
  }

  public Optional<Bytes> getInputData() {
    return Optional.of(transactionWithMetadata.getTransaction().getPayload());
  }

  public Optional<NormalBlockAdapter> getBlock(final DataFetchingEnvironment environment) {
    return transactionWithMetadata
        .getBlockHash()
        .flatMap(blockHash -> getBlockchainQueries(environment).blockByHash(blockHash))
        .map(NormalBlockAdapter::new);
  }

  public Optional<Long> getStatus(final DataFetchingEnvironment environment) {
    return Optional.ofNullable(transactionWithMetadata.getTransaction())
        .map(Transaction::getHash)
        .flatMap(rpt -> getBlockchainQueries(environment).transactionReceiptByTransactionHash(rpt))
        .map(TransactionReceiptWithMetadata::getReceipt)
        .flatMap(
            receipt ->
                receipt.getStatus() == -1
                    ? Optional.empty()
                    : Optional.of((long) receipt.getStatus()));
  }

  public Optional<Long> getGasUsed(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    final Optional<TransactionReceiptWithMetadata> rpt =
        query.transactionReceiptByTransactionHash(
            transactionWithMetadata.getTransaction().getHash());
    return rpt.map(TransactionReceiptWithMetadata::getGasUsed);
  }

  public Optional<Long> getCumulativeGasUsed(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    final Optional<TransactionReceiptWithMetadata> rpt =
        query.transactionReceiptByTransactionHash(
            transactionWithMetadata.getTransaction().getHash());
    if (rpt.isPresent()) {
      final TransactionReceipt receipt = rpt.get().getReceipt();
      return Optional.of(receipt.getCumulativeGasUsed());
    }
    return Optional.empty();
  }

  public Optional<AccountAdapter> getCreatedContract(final DataFetchingEnvironment environment) {
    final boolean contractCreated = transactionWithMetadata.getTransaction().isContractCreation();
    if (contractCreated) {
      final Optional<Address> addr = transactionWithMetadata.getTransaction().contractAddress();

      if (addr.isPresent()) {
        final BlockchainQueries query = getBlockchainQueries(environment);
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
    final BlockchainQueries query = getBlockchainQueries(environment);
    final Hash hash = transactionWithMetadata.getTransaction().getHash();
    final Optional<TransactionReceiptWithMetadata> maybeTransactionReceiptWithMetadata =
        query.transactionReceiptByTransactionHash(hash);
    final List<LogAdapter> results = new ArrayList<>();
    if (maybeTransactionReceiptWithMetadata.isPresent()) {
      final List<LogWithMetadata> logs =
          LogWithMetadata.generate(
              maybeTransactionReceiptWithMetadata.get().getReceipt(),
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

  public boolean getIsPrivate() {
    return transactionWithMetadata
        .getTransaction()
        .isGoQuorumPrivateTransaction(GoQuorumOptions.getGoQuorumCompatibilityMode());
  }

  public Optional<Bytes> getPrivateInputData() {
    final Transaction transaction = transactionWithMetadata.getTransaction();
    if (transaction.isGoQuorumPrivateTransaction(GoQuorumOptions.getGoQuorumCompatibilityMode())) {
      return Optional.ofNullable(transaction.getPayload());
    }
    return Optional.of(Bytes.EMPTY);
  }
}
