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
package tech.pegasys.pantheon.ethereum.api.graphql.internal.pojoadapter;

import tech.pegasys.pantheon.ethereum.api.BlockWithMetadata;
import tech.pegasys.pantheon.ethereum.api.TransactionWithMetadata;
import tech.pegasys.pantheon.ethereum.api.graphql.internal.BlockchainQuery;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import graphql.schema.DataFetchingEnvironment;

@SuppressWarnings("unused") // reflected by GraphQL
public class NormalBlockAdapter extends BlockAdapterBase {

  public NormalBlockAdapter(
      final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetaData) {
    super(blockWithMetaData.getHeader());
    this.blockWithMetaData = blockWithMetaData;
  }

  private final BlockWithMetadata<TransactionWithMetadata, Hash> blockWithMetaData;

  public Optional<Integer> getTransactionCount() {
    return Optional.of(blockWithMetaData.getTransactions().size());
  }

  public Optional<UInt256> getTotalDifficulty() {
    return Optional.of(blockWithMetaData.getTotalDifficulty());
  }

  public Optional<Integer> getOmmerCount() {
    return Optional.of(blockWithMetaData.getOmmers().size());
  }

  public List<UncleBlockAdapter> getOmmers(final DataFetchingEnvironment environment) {
    final BlockchainQuery query = getBlockchainQuery(environment);
    final List<Hash> ommers = blockWithMetaData.getOmmers();
    final List<UncleBlockAdapter> results = new ArrayList<>();
    final Hash hash = blockWithMetaData.getHeader().getHash();
    for (int i = 0; i < ommers.size(); i++) {
      final Optional<BlockHeader> header = query.getOmmer(hash, i);
      header.ifPresent(item -> results.add(new UncleBlockAdapter(item)));
    }

    return results;
  }

  public Optional<UncleBlockAdapter> getOmmerAt(final DataFetchingEnvironment environment) {
    final BlockchainQuery query = getBlockchainQuery(environment);
    final int index = environment.getArgument("index");
    final List<Hash> ommers = blockWithMetaData.getOmmers();
    if (ommers.size() > index) {
      final Hash hash = blockWithMetaData.getHeader().getHash();
      final Optional<BlockHeader> header = query.getOmmer(hash, index);
      return header.map(UncleBlockAdapter::new);
    }
    return Optional.empty();
  }

  public List<TransactionAdapter> getTransactions() {
    final List<TransactionWithMetadata> trans = blockWithMetaData.getTransactions();
    final List<TransactionAdapter> results = new ArrayList<>();
    for (final TransactionWithMetadata tran : trans) {
      results.add(new TransactionAdapter(tran));
    }
    return results;
  }

  public Optional<TransactionAdapter> getTransactionAt(final DataFetchingEnvironment environment) {
    final int index = environment.getArgument("index");
    final List<TransactionWithMetadata> trans = blockWithMetaData.getTransactions();

    if (trans.size() > index) {
      return Optional.of(new TransactionAdapter(trans.get(index)));
    }

    return Optional.empty();
  }
}
