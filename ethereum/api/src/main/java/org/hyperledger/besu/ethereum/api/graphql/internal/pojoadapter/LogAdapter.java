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
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogTopic;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;

@SuppressWarnings("unused") // reflected by GraphQL
public class LogAdapter extends AdapterBase {
  private final LogWithMetadata logWithMetadata;

  LogAdapter(final LogWithMetadata logWithMetadata) {
    this.logWithMetadata = logWithMetadata;
  }

  public Optional<Integer> getIndex() {
    return Optional.of(logWithMetadata.getLogIndex());
  }

  public List<Bytes32> getTopics() {
    final List<LogTopic> topics = logWithMetadata.getTopics();
    final List<Bytes32> result = new ArrayList<>();
    for (final LogTopic topic : topics) {
      result.add(Bytes32.leftPad(topic));
    }
    return result;
  }

  public Optional<BytesValue> getData() {
    return Optional.of(logWithMetadata.getData());
  }

  public Optional<TransactionAdapter> getTransaction(final DataFetchingEnvironment environment) {
    final BlockchainQuery query = getBlockchainQuery(environment);
    final Hash hash = logWithMetadata.getTransactionHash();
    final Optional<TransactionWithMetadata> tran = query.transactionByHash(hash);
    return tran.map(TransactionAdapter::new);
  }

  public Optional<AccountAdapter> getAccount(final DataFetchingEnvironment environment) {
    final BlockchainQuery query = getBlockchainQuery(environment);
    long blockNumber = logWithMetadata.getBlockNumber();
    final Long bn = environment.getArgument("block");
    if (bn != null) {
      blockNumber = bn;
    }

    return query
        .getWorldState(blockNumber)
        .map(ws -> new AccountAdapter(ws.get(logWithMetadata.getAddress())));
  }
}
