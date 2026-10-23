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
package org.hyperledger.besu.plugins.graphql.internal.pojoadapter;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.LogWithMetadata;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;
import org.hyperledger.besu.plugins.graphql.GraphQLContextType;
import org.hyperledger.besu.plugins.graphql.internal.query.ChainQueries;
import org.hyperledger.besu.plugins.graphql.internal.query.Quantities;
import org.hyperledger.besu.plugins.graphql.internal.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.plugins.graphql.internal.query.TransactionWithMetadata;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256s;

/**
 * The TransactionAdapter class provides methods to retrieve the transaction details.
 *
 * <p>This class is used to adapt a TransactionWithMetadata object into a format that can be used by
 * GraphQL. The TransactionWithMetadata object is provided at construction time.
 *
 * <p>The class provides methods to retrieve the hash, type, nonce, index, from, to, value, gas
 * price, max fee per gas, max priority fee per gas, max fee per blob gas, effective tip, gas, input
 * data, block, status, gas used, cumulative gas used, effective gas price, blob gas used, blob gas
 * price, created contract, logs, R, S, V, Y parity, access list, raw, raw receipt, and blob
 * versioned hashes of the transaction.
 */
@SuppressWarnings("unused") // reflected by GraphQL
public class TransactionAdapter extends AdapterBase {
  private final TransactionWithMetadata transactionWithMetadata;
  private Optional<TransactionReceiptWithMetadata> transactionReceiptWithMetadata;

  /**
   * Constructs a new TransactionAdapter object. Its receipt is looked up when a field needs it.
   *
   * @param transactionWithMetadata the TransactionWithMetadata object to adapt.
   */
  public TransactionAdapter(final TransactionWithMetadata transactionWithMetadata) {
    this.transactionWithMetadata = transactionWithMetadata;
  }

  /**
   * Constructs a new TransactionAdapter object with receipt.
   *
   * @param transactionWithMetadata the TransactionWithMetadata object to adapt.
   * @param transactionReceiptWithMetadata the TransactionReceiptWithMetadata object to adapt, null
   *     for a transaction without receipt.
   */
  public TransactionAdapter(
      final TransactionWithMetadata transactionWithMetadata,
      final TransactionReceiptWithMetadata transactionReceiptWithMetadata) {
    this.transactionWithMetadata = transactionWithMetadata;
    this.transactionReceiptWithMetadata = Optional.ofNullable(transactionReceiptWithMetadata);
  }

  /**
   * Reurns the receipt of the transaction.
   *
   * @param environment the data fetching environment.
   * @return the receipt of the transaction.
   */
  private Optional<TransactionReceiptWithMetadata> getReceipt(
      final DataFetchingEnvironment environment) {
    if (transactionReceiptWithMetadata == null) {
      final ChainQueries query = getBlockchainQueries(environment);

      final Transaction transaction = transactionWithMetadata.getTransaction();
      if (transaction == null) {
        transactionReceiptWithMetadata = Optional.empty();
      } else {
        transactionReceiptWithMetadata =
            query.transactionReceiptByTransactionHash(transaction.getHash());
      }
    }
    return transactionReceiptWithMetadata;
  }

  /**
   * Returns the hash of the transaction.
   *
   * @return the hash of the transaction.
   */
  public Hash getHash() {
    return transactionWithMetadata.getTransaction().getHash();
  }

  /**
   * Returns the type of the transaction.
   *
   * @return the type of the transaction.
   */
  public Optional<Integer> getType() {
    return Optional.of(transactionWithMetadata.getTransaction().getType().ordinal());
  }

  /**
   * Returns the nonce of the transaction.
   *
   * @return the nonce of the transaction.
   */
  public Long getNonce() {
    return transactionWithMetadata.getTransaction().getNonce();
  }

  /**
   * Returns the index of the transaction.
   *
   * @return the index of the transaction.
   */
  public Optional<Integer> getIndex() {
    return transactionWithMetadata.getTransactionIndex();
  }

  /**
   * Retrieves the sender of the transaction.
   *
   * <p>This method gets the block number and then retrieves the sender of the transaction. It then
   * reads the state of the sender's account at the given block number.
   *
   * @param environment the data fetching environment.
   * @return an AccountAdapter object representing the sender's account state at the given block
   *     number.
   */
  public AccountAdapter getFrom(final DataFetchingEnvironment environment) {
    final ChainQueries query = getBlockchainQueries(environment);
    final Long blockNumber =
        Optional.<Long>ofNullable(environment.getArgument("block"))
            .or(transactionWithMetadata::getBlockNumber)
            .orElseGet(query::headBlockNumber);

    final Address addr = transactionWithMetadata.getTransaction().getSender();
    return query
        .getAndMapWorldStateWithHash(
            blockNumber,
            (mutableWorldState, stateBlockHash) ->
                Optional.of(new AccountAdapter(mutableWorldState.get(addr), stateBlockHash)))
        .orElse(new EmptyAccountAdapter(addr));
  }

  /**
   * Retrieves the recipient of the transaction.
   *
   * <p>This method gets the block number and then retrieves the recipient of the transaction. It
   * then reads the state of the recipient's account at the given block number.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing an AccountAdapter object representing the recipient's account
   *     state at the given block number, or an empty Optional if the transaction does not have a
   *     recipient (i.e., it is a contract creation transaction).
   */
  public Optional<AccountAdapter> getTo(final DataFetchingEnvironment environment) {
    final ChainQueries query = getBlockchainQueries(environment);
    final Long blockNumber =
        Optional.<Long>ofNullable(environment.getArgument("block"))
            .or(transactionWithMetadata::getBlockNumber)
            .orElseGet(query::headBlockNumber);

    return transactionWithMetadata
        .getTransaction()
        .getTo()
        .map(Address.class::cast)
        .flatMap(
            address ->
                query
                    .getAndMapWorldStateWithHash(
                        blockNumber,
                        (ws, stateBlockHash) ->
                            Optional.of(
                                new AccountAdapter(address, ws.get(address), stateBlockHash)))
                    .or(() -> Optional.of(new EmptyAccountAdapter(address))));
  }

  /**
   * Retrieves the value of the transaction.
   *
   * @return a Wei object representing the value of the transaction.
   */
  public Wei getValue() {
    return transactionWithMetadata.getTransaction().getValue();
  }

  /**
   * Retrieves the gas price of the transaction.
   *
   * @return a Wei object representing the gas price of the transaction. If the transaction does not
   *     specify a gas price, this method returns zero.
   */
  public Wei getGasPrice() {
    return Quantities.toWei(transactionWithMetadata.getTransaction().getGasPrice())
        .orElse(Wei.ZERO);
  }

  /**
   * Retrieves the maximum fee per gas of the transaction.
   *
   * @return an Optional containing a Wei object representing the maximum fee per gas of the
   *     transaction, or an empty Optional if the transaction does not specify a maximum fee per
   *     gas.
   */
  public Optional<Wei> getMaxFeePerGas() {
    return Quantities.toWei(transactionWithMetadata.getTransaction().getMaxFeePerGas());
  }

  /**
   * Retrieves the maximum priority fee per gas of the transaction.
   *
   * @return an Optional containing a Wei object representing the maximum priority fee per gas of
   *     the transaction, or an empty Optional if the transaction does not specify a maximum
   *     priority fee per gas.
   */
  public Optional<Wei> getMaxPriorityFeePerGas() {
    return Quantities.toWei(transactionWithMetadata.getTransaction().getMaxPriorityFeePerGas());
  }

  /**
   * Retrieves the maximum fee per blob gas of the transaction.
   *
   * @return an Optional containing a Wei object representing the maximum fee per blob gas of the
   *     transaction, or an empty Optional if the transaction does not specify a maximum fee per
   *     blob gas.
   */
  public Optional<Wei> getMaxFeePerBlobGas() {
    return Quantities.toWei(transactionWithMetadata.getTransaction().getMaxFeePerBlobGas());
  }

  /**
   * Retrieves the effective tip of the transaction.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing a Wei object representing the effective tip of the transaction,
   *     or an empty Optional if the transaction does not specify an effective tip.
   */
  public Optional<Wei> getEffectiveTip(final DataFetchingEnvironment environment) {
    return getReceipt(environment)
        .map(rwm -> effectivePriorityFeePerGas(rwm.getTransaction(), rwm.getBaseFee()));
  }

  /**
   * Retrieves the gas limit of the transaction.
   *
   * @return a Long object representing the gas limit of the transaction.
   */
  public Long getGas() {
    return transactionWithMetadata.getTransaction().getGasLimit();
  }

  /**
   * Retrieves the input data of the transaction.
   *
   * @return a Bytes object representing the input data of the transaction.
   */
  public Bytes getInputData() {
    return transactionWithMetadata.getTransaction().getPayload();
  }

  /**
   * Retrieves the block of the transaction.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing a NormalBlockAdapter object representing the block of the
   *     transaction, or an empty Optional if the transaction does not specify a block.
   */
  public Optional<NormalBlockAdapter> getBlock(final DataFetchingEnvironment environment) {
    return transactionWithMetadata
        .getBlockHash()
        .flatMap(blockHash -> getBlockchainQueries(environment).blockByHash(blockHash))
        .map(NormalBlockAdapter::new);
  }

  /**
   * Retrieves the status of the transaction.
   *
   * <p>This method uses the getReceipt method to get the receipt of the transaction. It then checks
   * the status of the receipt. If the status is -1, it returns an empty Optional. Otherwise, it
   * returns an Optional containing the status of the receipt.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing a Long object representing the status of the transaction, or an
   *     empty Optional if the status of the receipt is -1.
   */
  public Optional<Long> getStatus(final DataFetchingEnvironment environment) {
    return getReceipt(environment)
        .map(TransactionReceiptWithMetadata::getReceipt)
        .flatMap(
            receipt ->
                receipt.getStatus() == -1
                    ? Optional.empty()
                    : Optional.of((long) receipt.getStatus()));
  }

  /**
   * Retrieves the revert reason of the transaction, if any.
   *
   * <p>This method uses the getReceipt method to get the receipt of the transaction. It then checks
   * the revert reason of the receipt. It would be an empty Optional for successful transactions.
   * Otherwise, it returns an Optional containing the revert reason.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing a Bytes object representing the revert reason of the
   *     transaction, or an empty Optional .
   */
  public Optional<Bytes> getRevertReason(final DataFetchingEnvironment environment) {
    return getReceipt(environment)
        .map(TransactionReceiptWithMetadata::getReceipt)
        .flatMap(TransactionReceipt::getRevertReason);
  }

  /**
   * Retrieves the gas used by the transaction.
   *
   * <p>This method uses the getReceipt method to get the receipt of the transaction. It then
   * returns an Optional containing the gas used by the transaction.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing a Long object representing the gas used by the transaction.
   */
  public Optional<Long> getGasUsed(final DataFetchingEnvironment environment) {
    return getReceipt(environment).map(TransactionReceiptWithMetadata::getGasUsed);
  }

  /**
   * Retrieves the cumulative gas used by the transaction.
   *
   * <p>This method uses the getReceipt method to get the receipt of the transaction. It then
   * returns an Optional containing the cumulative gas used by the transaction.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing a Long object representing the cumulative gas used by the
   *     transaction.
   */
  public Optional<Long> getCumulativeGasUsed(final DataFetchingEnvironment environment) {
    return getReceipt(environment).map(rpt -> rpt.getReceipt().getCumulativeGasUsed());
  }

  /**
   * Retrieves the effective gas price of the transaction.
   *
   * <p>This method uses the getReceipt method to get the receipt of the transaction. It then
   * returns an Optional containing the effective gas price of the transaction.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing a Wei object representing the effective gas price of the
   *     transaction.
   */
  public Optional<Wei> getEffectiveGasPrice(final DataFetchingEnvironment environment) {
    return getReceipt(environment)
        .map(rwm -> effectiveGasPrice(rwm.getTransaction(), rwm.getBaseFee()));
  }

  /**
   * Retrieves the blob gas used by the transaction.
   *
   * <p>This method uses the getReceipt method to get the receipt of the transaction. It then
   * returns an Optional containing the blob gas used by the transaction.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing a Long object representing the blob gas used by the transaction.
   */
  public Optional<Long> getBlobGasUsed(final DataFetchingEnvironment environment) {
    return getReceipt(environment).flatMap(TransactionReceiptWithMetadata::getBlobGasUsed);
  }

  /**
   * Retrieves the blob gas price of the transaction.
   *
   * <p>This method uses the getReceipt method to get the receipt of the transaction. It then
   * returns an Optional containing the blob gas price of the transaction.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing a Wei object representing the blob gas price of the transaction.
   */
  public Optional<Wei> getBlobGasPrice(final DataFetchingEnvironment environment) {
    return getReceipt(environment).flatMap(TransactionReceiptWithMetadata::getBlobGasPrice);
  }

  /**
   * Retrieves the contract created by the transaction.
   *
   * <p>This method checks if the transaction is a contract creation transaction. If it is, it
   * retrieves the address of the created contract. It then gets the block number and then retrieves
   * the state of the created contract's account at the given block number.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing an AccountAdapter object representing the created contract's
   *     account state at the given block number, or an empty Optional if the transaction is not a
   *     contract creation transaction or if the block number is not specified.
   */
  public Optional<AccountAdapter> getCreatedContract(final DataFetchingEnvironment environment) {
    final boolean contractCreated = transactionWithMetadata.getTransaction().isContractCreation();
    if (contractCreated) {
      final Optional<Address> addr = transactionWithMetadata.getTransaction().contractAddress();

      if (addr.isPresent()) {
        final ChainQueries query = getBlockchainQueries(environment);
        final Optional<Long> txBlockNumber = transactionWithMetadata.getBlockNumber();
        final Optional<Long> bn = Optional.ofNullable(environment.getArgument("block"));
        if (txBlockNumber.isEmpty() && bn.isEmpty()) {
          return Optional.empty();
        }
        final long blockNumber = bn.orElseGet(txBlockNumber::get);
        return query
            .getAndMapWorldStateWithHash(
                blockNumber,
                (ws, stateBlockHash) ->
                    Optional.of(new AccountAdapter(ws.get(addr.get()), stateBlockHash)))
            .or(() -> Optional.of(new EmptyAccountAdapter(addr.get())));
      }
    }
    return Optional.empty();
  }

  /**
   * Retrieves the logs of the transaction.
   *
   * <p>This method gets the block header and the receipt of the transaction. It then retrieves the
   * logs of the transaction and adapts them into a format that can be used by GraphQL.
   *
   * @param environment the data fetching environment.
   * @return a List of LogAdapter objects representing the logs of the transaction. If the
   *     transaction does not have a receipt, this method returns an empty list.
   */
  public List<LogAdapter> getLogs(final DataFetchingEnvironment environment) {
    final ChainQueries query = getBlockchainQueries(environment);

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
        query.transactionReceiptByTransactionHash(hash);
    final List<LogAdapter> results = new ArrayList<>();
    if (maybeTransactionReceiptWithMetadata.isPresent()) {
      final List<LogWithMetadata> logs =
          query.matchingLogs(maybeBlockHeader.get().getBlockHash(), transactionWithMetadata);
      for (final LogWithMetadata log : logs) {
        results.add(new LogAdapter(log));
      }
    }
    return results;
  }

  /**
   * Retrieves the R component of the transaction's signature.
   *
   * @return a BigInteger object representing the R component of the transaction's signature.
   */
  public BigInteger getR() {
    return transactionWithMetadata.getTransaction().getR();
  }

  /**
   * Retrieves the S component of the transaction's signature.
   *
   * @return a BigInteger object representing the S component of the transaction's signature.
   */
  public BigInteger getS() {
    return transactionWithMetadata.getTransaction().getS();
  }

  /**
   * Retrieves the V component of the transaction's signature.
   *
   * <p>If the transaction type is less than the BLOB transaction type and V is null, it returns the
   * Y parity of the transaction. Otherwise, it returns V.
   *
   * @return an Optional containing a BigInteger object representing the V component of the
   *     transaction's signature, or an Optional containing the Y parity of the transaction if V is
   *     null and the transaction type is less than the BLOB transaction type.
   */
  public Optional<BigInteger> getV() {
    BigInteger v = transactionWithMetadata.getTransaction().getV();
    return Optional.ofNullable(
        v == null
                && (transactionWithMetadata.getTransaction().getType().getEthSerializedType()
                    < TransactionType.BLOB.getEthSerializedType())
            ? transactionWithMetadata.getTransaction().getYParity()
            : v);
  }

  /**
   * Retrieves the Y parity of the transaction's signature.
   *
   * @return an Optional containing a BigInteger object representing the Y parity of the
   *     transaction's signature.
   */
  public Optional<BigInteger> getYParity() {
    return Optional.ofNullable(transactionWithMetadata.getTransaction().getYParity());
  }

  /**
   * Retrieves the access list of the transaction.
   *
   * @return a List of AccessListEntryAdapter objects representing the access list of the
   *     transaction.
   */
  public List<AccessListEntryAdapter> getAccessList() {
    return transactionWithMetadata
        .getTransaction()
        .getAccessList()
        .map(l -> l.stream().map(AccessListEntryAdapter::new).toList())
        .orElse(List.of());
  }

  /**
   * Retrieves the raw transaction data.
   *
   * <p>This is the transaction encoded as in a block body: typed transactions are wrapped in an RLP
   * string.
   *
   * @return an Optional containing a Bytes object representing the raw transaction data.
   */
  public Optional<Bytes> getRaw() {
    return Optional.of(transactionWithMetadata.getTransaction().encoded());
  }

  /**
   * Retrieves the raw receipt of the transaction.
   *
   * <p>This method uses the getReceipt method to get the receipt of the transaction. It then
   * encodes the receipt and returns it.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing a Bytes object representing the raw receipt of the transaction.
   */
  public Optional<Bytes> getRawReceipt(final DataFetchingEnvironment environment) {
    return getReceipt(environment)
        .map(
            receipt -> {
              final RlpConverterService rlpConverterService =
                  environment.getGraphQlContext().get(GraphQLContextType.RLP_CONVERTER_SERVICE);
              return rlpConverterService.buildRlpFromReceipt(receipt.getReceipt());
            });
  }

  /**
   * Retrieves the versioned hashes of the transaction.
   *
   * @return a List of VersionedHash objects representing the versioned hashes of the transaction.
   */
  public List<VersionedHash> getBlobVersionedHashes() {
    return transactionWithMetadata.getTransaction().getVersionedHashes().orElse(List.of());
  }

  /**
   * Computes the priority fee per gas a transaction pays in a block, the same way as Besu does.
   *
   * @param transaction the transaction
   * @param maybeBaseFee the base fee of the block
   * @return the effective priority fee per gas
   */
  static Wei effectivePriorityFeePerGas(
      final Transaction transaction, final Optional<Wei> maybeBaseFee) {
    return maybeBaseFee
        .map(
            baseFee -> {
              if (transaction.getType().supports1559FeeMarket()) {
                final Wei maxFeePerGas =
                    Quantities.toWei(transaction.getMaxFeePerGas().orElseThrow());
                if (baseFee.greaterOrEqualThan(maxFeePerGas)) {
                  return Wei.ZERO;
                }
                return UInt256s.min(
                    Quantities.toWei(transaction.getMaxPriorityFeePerGas().orElseThrow()),
                    maxFeePerGas.subtract(baseFee));
              } else {
                final Wei gasPrice = Quantities.toWei(transaction.getGasPrice().orElseThrow());
                if (baseFee.greaterOrEqualThan(gasPrice)) {
                  return Wei.ZERO;
                }
                return gasPrice.subtract(baseFee);
              }
            })
        .orElseGet(() -> Quantities.toWei(transaction.getGasPrice()).orElse(Wei.ZERO));
  }

  /**
   * Computes the gas price a transaction pays in a block, the same way as Besu does.
   *
   * @param transaction the transaction
   * @param baseFeePerGas the base fee of the block
   * @return the effective gas price
   */
  static Wei effectiveGasPrice(final Transaction transaction, final Optional<Wei> baseFeePerGas) {
    return effectivePriorityFeePerGas(transaction, baseFeePerGas)
        .addExact(baseFeePerGas.orElse(Wei.ZERO));
  }
}
