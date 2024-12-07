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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLContextType;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;

/**
 * This class is an adapter for the BlockWithMetadata class.
 *
 * <p>It extends the BlockAdapterBase class and provides methods to get the transaction count, total
 * difficulty, ommer count, ommers, transactions, and specific ommer and transaction at a given
 * index associated with a block.
 */
@SuppressWarnings("unused") // reflected by GraphQL
public class NormalBlockAdapter extends BlockAdapterBase {

  /**
   * Constructor for NormalBlockAdapter.
   *
   * <p>It initializes the blockWithMetaData field with the provided argument and calls the parent
   * constructor with the header of the provided blockWithMetaData.
   *
   * @param blockWithMetaData the block with metadata to be adapted.
   */
  public NormalBlockAdapter(
      final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetaData) {
    super(blockWithMetaData.getHeader());
    this.blockWithMetaData = blockWithMetaData;
  }

  private final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetaData;

  /**
   * Returns the transaction count of the block.
   *
   * @return the transaction count of the block.
   */
  public Optional<Integer> getTransactionCount() {
    return Optional.of(blockWithMetaData.getTransactions().size());
  }

  /**
   * Returns the total difficulty of the block.
   *
   * @return the total difficulty of the block.
   */
  public Difficulty getTotalDifficulty() {
    return blockWithMetaData.getTotalDifficulty();
  }

  /**
   * Returns the ommer count of the block.
   *
   * @return the ommer count of the block.
   */
  public Optional<Integer> getOmmerCount() {
    return Optional.of(blockWithMetaData.getOmmers().size());
  }

  /**
   * Returns the ommers of the block.
   *
   * @param environment the data fetching environment.
   * @return a list of UncleBlockAdapter for the ommers of the block.
   */
  public List<UncleBlockAdapter> getOmmers(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    final List<Hash> ommers = blockWithMetaData.getOmmers();
    final List<UncleBlockAdapter> results = new ArrayList<>();
    final Hash hash = blockWithMetaData.getHeader().getHash();
    for (int i = 0; i < ommers.size(); i++) {
      final Optional<BlockHeader> header = query.getOmmer(hash, i);
      header.ifPresent(item -> results.add(new UncleBlockAdapter(item)));
    }

    return results;
  }

  /**
   * Returns the ommer at a given index of the block.
   *
   * @param environment the data fetching environment.
   * @return an UncleBlockAdapter for the ommer at the given index of the block.
   */
  public Optional<UncleBlockAdapter> getOmmerAt(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    final int index = ((Number) environment.getArgument("index")).intValue();
    final List<Hash> ommers = blockWithMetaData.getOmmers();
    if (ommers.size() > index) {
      final Hash hash = blockWithMetaData.getHeader().getHash();
      final Optional<BlockHeader> header = query.getOmmer(hash, index);
      return header.map(UncleBlockAdapter::new);
    }
    return Optional.empty();
  }

  /**
   * Returns a list of TransactionAdapter objects for the transactions in the block.
   *
   * <p>Each TransactionAdapter object is created by adapting a TransactionWithMetadata object.
   *
   * @param environment the data fetching environment.
   * @return a list of TransactionAdapter objects for the transactions in the block.
   */
  public List<TransactionAdapter> getTransactions(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    final Hash hash = blockWithMetaData.getHeader().getHash();
    final ProtocolSchedule protocolSchedule =
        environment.getGraphQlContext().get(GraphQLContextType.PROTOCOL_SCHEDULE);

    final List<TransactionWithMetadata> trans = blockWithMetaData.getTransactions();
    final List<TransactionReceiptWithMetadata> transReceipts =
        query.transactionReceiptsByBlockHash(hash, protocolSchedule).get();

    final List<TransactionAdapter> results = new ArrayList<>();
    for (int i = 0; i < trans.size(); i++) {
      results.add(new TransactionAdapter(trans.get(i), transReceipts.get(i)));
    }
    return results;
  }

  /**
   * Returns a TransactionAdapter object for the transaction at the given index in the block.
   *
   * <p>The index is retrieved from the data fetching environment. If there is no transaction at the
   * given index, an empty Optional is returned.
   *
   * @param environment the data fetching environment.
   * @return an Optional containing a TransactionAdapter object for the transaction at the given
   *     index in the block, or an empty Optional if there is no transaction at the given index.
   */
  public Optional<TransactionAdapter> getTransactionAt(final DataFetchingEnvironment environment) {
    final int index = ((Number) environment.getArgument("index")).intValue();
    final List<TransactionWithMetadata> trans = blockWithMetaData.getTransactions();

    if (trans.size() > index) {
      return Optional.of(new TransactionAdapter(trans.get(index)));
    }

    return Optional.empty();
  }
}
