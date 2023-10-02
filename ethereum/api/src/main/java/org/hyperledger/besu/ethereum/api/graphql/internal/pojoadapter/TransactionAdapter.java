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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLContextType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;

@SuppressWarnings("unused") // reflected by GraphQL
public class TransactionAdapter extends AdapterBase {
  private final TransactionWithMetadata transactionWithMetadata;
  private Optional<TransactionReceiptWithMetadata> transactionReceiptWithMetadata;

  public TransactionAdapter(final @Nonnull TransactionWithMetadata transactionWithMetadata) {
    this.transactionWithMetadata = transactionWithMetadata;
  }

  private Optional<TransactionReceiptWithMetadata> getReceipt(
      final DataFetchingEnvironment environment) {
    if (transactionReceiptWithMetadata == null) {
      final BlockchainQueries query = getBlockchainQueries(environment);
      final ProtocolSchedule protocolSchedule =
          environment.getGraphQlContext().get(GraphQLContextType.PROTOCOL_SCHEDULE);

      final Transaction transaction = transactionWithMetadata.getTransaction();
      if (transaction == null) {
        transactionReceiptWithMetadata = Optional.empty();
      } else {
        transactionReceiptWithMetadata =
            query.transactionReceiptByTransactionHash(transaction.getHash(), protocolSchedule);
      }
    }
    return transactionReceiptWithMetadata;
  }

  public Hash getHash() {
    return transactionWithMetadata.getTransaction().getHash();
  }

  public Optional<Integer> getType() {
    return Optional.of(transactionWithMetadata.getTransaction().getType().ordinal());
  }

  public Long getNonce() {
    return transactionWithMetadata.getTransaction().getNonce();
  }

  public Optional<Integer> getIndex() {
    return transactionWithMetadata.getTransactionIndex();
  }

  public AccountAdapter getFrom(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    Long blockNumber = environment.getArgument("block");
    if (blockNumber == null) {
      blockNumber = transactionWithMetadata.getBlockNumber().orElseGet(query::headBlockNumber);
    }
    return query
        .getAndMapWorldState(
            blockNumber,
            mutableWorldState ->
                Optional.of(
                    new AccountAdapter(
                        mutableWorldState.get(
                            transactionWithMetadata.getTransaction().getSender()))))
        .get();
  }

  public Optional<AccountAdapter> getTo(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    Long blockNumber = environment.getArgument("block");
    if (blockNumber == null) {
      blockNumber = transactionWithMetadata.getBlockNumber().orElseGet(query::headBlockNumber);
    }

    return query.getAndMapWorldState(
        blockNumber,
        ws ->
            transactionWithMetadata
                .getTransaction()
                .getTo()
                .map(address -> new AccountAdapter(address, ws.get(address))));
  }

  public Wei getValue() {
    return transactionWithMetadata.getTransaction().getValue();
  }

  public Wei getGasPrice() {
    return transactionWithMetadata.getTransaction().getGasPrice().orElse(Wei.ZERO);
  }

  public Optional<Wei> getMaxFeePerGas() {
    return transactionWithMetadata.getTransaction().getMaxFeePerGas();
  }

  public Optional<Wei> getMaxPriorityFeePerGas() {
    return transactionWithMetadata.getTransaction().getMaxPriorityFeePerGas();
  }

  public Optional<Wei> getMaxFeePerBlobGas() {
    return transactionWithMetadata.getTransaction().getMaxFeePerBlobGas();
  }

  public Optional<Wei> getEffectiveTip(final DataFetchingEnvironment environment) {
    return getReceipt(environment)
        .map(rwm -> rwm.getTransaction().getEffectivePriorityFeePerGas(rwm.getBaseFee()));
  }

  public Long getGas() {
    return transactionWithMetadata.getTransaction().getGasLimit();
  }

  public Bytes getInputData() {
    return transactionWithMetadata.getTransaction().getPayload();
  }

  public Optional<NormalBlockAdapter> getBlock(final DataFetchingEnvironment environment) {
    return transactionWithMetadata
        .getBlockHash()
        .flatMap(blockHash -> getBlockchainQueries(environment).blockByHash(blockHash))
        .map(NormalBlockAdapter::new);
  }

  public Optional<Long> getStatus(final DataFetchingEnvironment environment) {
    return getReceipt(environment)
        .map(TransactionReceiptWithMetadata::getReceipt)
        .flatMap(
            receipt ->
                receipt.getStatus() == -1
                    ? Optional.empty()
                    : Optional.of((long) receipt.getStatus()));
  }

  public Optional<Long> getGasUsed(final DataFetchingEnvironment environment) {
    return getReceipt(environment).map(TransactionReceiptWithMetadata::getGasUsed);
  }

  public Optional<Long> getCumulativeGasUsed(final DataFetchingEnvironment environment) {
    return getReceipt(environment).map(rpt -> rpt.getReceipt().getCumulativeGasUsed());
  }

  public Optional<Wei> getEffectiveGasPrice(final DataFetchingEnvironment environment) {
    return getReceipt(environment)
        .map(rwm -> rwm.getTransaction().getEffectiveGasPrice(rwm.getBaseFee()));
  }

  public Optional<Long> getBlobGasUsed(final DataFetchingEnvironment environment) {
    return getReceipt(environment).flatMap(TransactionReceiptWithMetadata::getBlobGasUsed);
  }

  public Optional<Wei> getBlobGasPrice(final DataFetchingEnvironment environment) {
    return getReceipt(environment).flatMap(TransactionReceiptWithMetadata::getBlobGasPrice);
  }

  public Optional<AccountAdapter> getCreatedContract(final DataFetchingEnvironment environment) {
    final boolean contractCreated = transactionWithMetadata.getTransaction().isContractCreation();
    if (contractCreated) {
      final Optional<Address> addr = transactionWithMetadata.getTransaction().contractAddress();

      if (addr.isPresent()) {
        final BlockchainQueries query = getBlockchainQueries(environment);
        final Optional<Long> txBlockNumber = transactionWithMetadata.getBlockNumber();
        final Optional<Long> bn = Optional.ofNullable(environment.getArgument("block"));
        if (txBlockNumber.isEmpty() && bn.isEmpty()) {
          return Optional.empty();
        }
        final long blockNumber = bn.orElseGet(txBlockNumber::get);
        return query.getAndMapWorldState(
            blockNumber, ws -> Optional.of(new AccountAdapter(ws.get(addr.get()))));
      }
    }
    return Optional.empty();
  }

  public List<LogAdapter> getLogs(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    final ProtocolSchedule protocolSchedule =
        environment.getGraphQlContext().get(GraphQLContextType.PROTOCOL_SCHEDULE);

    final Hash hash = transactionWithMetadata.getTransaction().getHash();

    final Optional<BlockHeader> maybeBlockHeader =
        transactionWithMetadata.getBlockNumber().flatMap(query::getBlockHeaderByNumber);

    if (maybeBlockHeader.isEmpty()) {
      throw new RuntimeException(
          "Cannot get block ("
              + transactionWithMetadata.getBlockNumber()
              + ") for transaction "
              + transactionWithMetadata.getTransaction().getHash());
    }

    final Optional<TransactionReceiptWithMetadata> maybeTransactionReceiptWithMetadata =
        query.transactionReceiptByTransactionHash(hash, protocolSchedule);
    final List<LogAdapter> results = new ArrayList<>();
    if (maybeTransactionReceiptWithMetadata.isPresent()) {
      final List<LogWithMetadata> logs =
          query.matchingLogs(
              maybeBlockHeader.get().getBlockHash(), transactionWithMetadata, () -> true);
      for (final LogWithMetadata log : logs) {
        results.add(new LogAdapter(log));
      }
    }
    return results;
  }

  public List<AccessListEntryAdapter> getAccessList() {
    return transactionWithMetadata
        .getTransaction()
        .getAccessList()
        .map(l -> l.stream().map(AccessListEntryAdapter::new).toList())
        .orElse(List.of());
  }

  public Optional<Bytes> getRaw() {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    transactionWithMetadata.getTransaction().writeTo(rlpOutput);
    return Optional.of(rlpOutput.encoded());
  }

  public Optional<Bytes> getRawReceipt(final DataFetchingEnvironment environment) {
    return getReceipt(environment)
        .map(
            receipt -> {
              final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
              receipt.getReceipt().writeTo(rlpOutput);
              return rlpOutput.encoded();
            });
  }

  public List<VersionedHash> getBlobVersionedHashes() {
    return transactionWithMetadata.getTransaction().getVersionedHashes().orElse(List.of());
  }
}
