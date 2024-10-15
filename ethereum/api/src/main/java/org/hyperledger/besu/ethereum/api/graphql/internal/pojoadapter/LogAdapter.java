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
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.evm.log.LogTopic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;

/**
 * This class is an adapter for the LogWithMetadata class.
 *
 * <p>It extends the AdapterBase class and provides methods to get the index, topics, data,
 * transaction, and account associated with a log.
 */
@SuppressWarnings("unused") // reflected by GraphQL
public class LogAdapter extends AdapterBase {
  private final LogWithMetadata logWithMetadata;

  /**
   * Constructor for LogAdapter.
   *
   * <p>It initializes the logWithMetadata field with the provided argument.
   *
   * @param logWithMetadata the log with metadata to be adapted.
   */
  public LogAdapter(final LogWithMetadata logWithMetadata) {
    this.logWithMetadata = logWithMetadata;
  }

  /**
   * Returns the index of the log.
   *
   * @return the index of the log.
   */
  public Integer getIndex() {
    return logWithMetadata.getLogIndex();
  }

  /**
   * Returns the topics of the log.
   *
   * @return a list of topics of the log.
   */
  public List<LogTopic> getTopics() {
    final List<LogTopic> topics = logWithMetadata.getTopics();
    return new ArrayList<>(topics);
  }

  /**
   * Returns the data of the log.
   *
   * @return the data of the log.
   */
  public Bytes getData() {
    return logWithMetadata.getData();
  }

  /**
   * Returns the transaction associated with the log.
   *
   * @param environment the data fetching environment.
   * @return a TransactionAdapter for the transaction associated with the log.
   * @throws java.util.NoSuchElementException if the transaction is not found.
   */
  public TransactionAdapter getTransaction(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    final Hash hash = logWithMetadata.getTransactionHash();
    final Optional<TransactionWithMetadata> tran = query.transactionByHash(hash);
    return tran.map(TransactionAdapter::new).orElseThrow();
  }

  /**
   * Returns the account associated with the log.
   *
   * @param environment the data fetching environment.
   * @return an AccountAdapter for the account associated with the log.
   */
  public AccountAdapter getAccount(final DataFetchingEnvironment environment) {
    final BlockchainQueries query = getBlockchainQueries(environment);
    long blockNumber = logWithMetadata.getBlockNumber();
    final Long bn = environment.getArgument("block");
    if (bn != null) {
      blockNumber = bn;
    }

    final Address logger = logWithMetadata.getLogger();
    return query
        .getAndMapWorldState(blockNumber, ws -> Optional.of(new AccountAdapter(ws.get(logger))))
        .orElse(new EmptyAccountAdapter(logger));
  }
}
