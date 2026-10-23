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
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.LogTopic;
import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.LogWithMetadata;
import org.hyperledger.besu.plugin.services.BlockchainQueryService;
import org.hyperledger.besu.plugin.services.TransactionSimulationService.SimulationParameters;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;
import org.hyperledger.besu.plugins.graphql.GraphQLContextType;
import org.hyperledger.besu.plugins.graphql.internal.query.BlockWithMetadata;
import org.hyperledger.besu.plugins.graphql.internal.query.ChainQueries;
import org.hyperledger.besu.plugins.graphql.internal.query.Quantities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.primitives.Longs;
import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * The BlockAdapterBase class extends the AdapterBase class. It provides methods to get and
 * manipulate block data.
 */
@SuppressWarnings("unused") // reflected by GraphQL
public class BlockAdapterBase extends AdapterBase {

  private final BlockHeader header;

  /**
   * Constructs a new BlockAdapterBase with the given BlockHeader.
   *
   * @param header the BlockHeader to be adapted
   */
  BlockAdapterBase(final BlockHeader header) {
    this.header = header;
  }

  /**
   * Returns the parent block of the current block.
   *
   * @param environment the DataFetchingEnvironment
   * @return an Optional containing the parent block if it exists, otherwise an empty Optional
   */
  public Optional<NormalBlockAdapter> getParent(final DataFetchingEnvironment environment) {
    final ChainQueries query = getBlockchainQueries(environment);
    final Hash parentHash = header.getParentHash();
    final Optional<BlockWithMetadata> block = query.blockByHash(parentHash);
    return block.map(NormalBlockAdapter::new);
  }

  /**
   * Returns the hash of the current block.
   *
   * @return the hash of the block
   */
  public Hash getHash() {
    return header.getBlockHash();
  }

  /**
   * Returns the nonce of the current block.
   *
   * @return the nonce of the block
   */
  public Bytes getNonce() {
    final long nonce = header.getNonce();
    final byte[] bytes = Longs.toByteArray(nonce);
    return Bytes.wrap(bytes);
  }

  /**
   * Returns the transactions root of the current block.
   *
   * @return the transactions root of the block
   */
  public Hash getTransactionsRoot() {
    return header.getTransactionsRoot();
  }

  /**
   * Returns the state root of the current block.
   *
   * @return the state root of the block
   */
  public Hash getStateRoot() {
    return header.getStateRoot();
  }

  /**
   * Returns the receipts root of the current block.
   *
   * @return the receipts root of the block
   */
  public Hash getReceiptsRoot() {
    return header.getReceiptsRoot();
  }

  /**
   * Returns the miner of the current block.
   *
   * @param environment the DataFetchingEnvironment
   * @return an AccountAdapter instance representing the miner of the block
   */
  public AccountAdapter getMiner(final DataFetchingEnvironment environment) {

    final ChainQueries query = getBlockchainQueries(environment);
    long blockNumber = header.getNumber();
    final Long bn = environment.getArgument("block");
    if (bn != null) {
      blockNumber = bn;
    }

    return query
        .getAndMapWorldStateWithHash(
            blockNumber,
            (ws, stateBlockHash) ->
                Optional.ofNullable(ws.get(header.getCoinbase()))
                    .map(account -> new AccountAdapter(account, stateBlockHash)))
        .orElseGet(() -> new EmptyAccountAdapter(header.getCoinbase()));
  }

  /**
   * Returns the extra data of the current block.
   *
   * @return the extra data of the block
   */
  public Bytes getExtraData() {
    return header.getExtraData();
  }

  /**
   * Returns the base fee per gas of the current block.
   *
   * @return the base fee per gas of the block
   */
  public Optional<Wei> getBaseFeePerGas() {
    return Quantities.toWei(header.getBaseFee());
  }

  /**
   * Returns the gas limit of the current block.
   *
   * @return the gas limit of the block
   */
  public Long getGasLimit() {
    return header.getGasLimit();
  }

  /**
   * Returns the gas used by the current block.
   *
   * @return the gas used by the block
   */
  public Long getGasUsed() {
    return header.getGasUsed();
  }

  /**
   * Returns the timestamp of the current block.
   *
   * @return the timestamp of the block
   */
  public Long getTimestamp() {
    return header.getTimestamp();
  }

  /**
   * Returns the logs bloom of the current block.
   *
   * @return the logs bloom of the block
   */
  public Bytes getLogsBloom() {
    return header.getLogsBloom().getBytes();
  }

  /**
   * Returns the mix hash of the current block.
   *
   * @return the mix hash of the block
   */
  public Hash getMixHash() {
    return header.getMixHash();
  }

  /**
   * Returns the difficulty of the current block.
   *
   * @return the difficulty of the block
   */
  public Bytes getDifficulty() {
    return Quantities.toBytes(header.getDifficulty());
  }

  /**
   * Returns the ommer hash of the current block.
   *
   * @return the ommer hash of the block
   */
  public Hash getOmmerHash() {
    return header.getOmmersHash();
  }

  /**
   * Returns the number of the current block.
   *
   * @return the number of the block
   */
  public Long getNumber() {
    return header.getNumber();
  }

  /**
   * Returns an AccountAdapter instance for a given address at the current block.
   *
   * @param environment the DataFetchingEnvironment
   * @return an AccountAdapter instance representing the account of the given address at the current
   *     block
   */
  public AccountAdapter getAccount(final DataFetchingEnvironment environment) {
    final ChainQueries query = getBlockchainQueries(environment);
    final long bn = header.getNumber();
    final Address address = environment.getArgument("address");
    return query
        .getAndMapWorldStateWithHash(
            bn,
            (ws, stateBlockHash) ->
                Optional.of(new AccountAdapter(ws.get(address), stateBlockHash, Optional.of(bn))))
        .get();
  }

  /**
   * Returns a list of logs for the current block that match a given filter.
   *
   * @param environment the DataFetchingEnvironment
   * @return a list of LogAdapter instances representing the logs of the current block that match
   *     the filter
   */
  public List<LogAdapter> getLogs(final DataFetchingEnvironment environment) {

    final Map<String, Object> filter = environment.getArgument("filter");

    @SuppressWarnings("unchecked")
    final List<Address> addresses = (List<Address>) filter.get("addresses");
    // `topics` is nullable in the schema, and the schema's own documentation says "[] or nil
    // matches any topic list", so an omitted value has to behave like an empty one rather than
    // being dereferenced. graphql-java puts the key in the map with a null value when a client
    // writes `topics: null` explicitly, so getOrDefault is not enough on its own.
    // (`addresses` may stay null: an empty address list matches any address.)
    @SuppressWarnings("unchecked")
    final List<List<LogTopic>> topics =
        Optional.ofNullable((List<List<LogTopic>>) filter.get("topics")).orElse(List.of());

    final List<List<Bytes32>> transformedTopics = toQueryTopics(topics);

    final BlockchainQueryService blockchain =
        environment.getGraphQlContext().get(GraphQLContextType.BLOCKCHAIN_QUERY_SERVICE);

    final Hash hash = header.getBlockHash();
    final List<LogWithMetadata> logs =
        blockchain.getLogs(hash, addresses == null ? List.of() : addresses, transformedTopics);
    final List<LogAdapter> results = new ArrayList<>();
    for (final LogWithMetadata log : logs) {
      results.add(new LogAdapter(log));
    }
    return results;
  }

  /**
   * Converts the topics of a GraphQL log filter to the topics of a log query: a position with an
   * empty list of topics matches any topic.
   *
   * @param topics the topics of the GraphQL filter
   * @return the topics of the log query
   */
  public static List<List<Bytes32>> toQueryTopics(final List<List<LogTopic>> topics) {
    final List<List<Bytes32>> transformedTopics = new ArrayList<>();
    for (final List<LogTopic> topic : topics) {
      if (topic.isEmpty()) {
        transformedTopics.add(Collections.singletonList(null));
      } else {
        final List<Bytes32> position = new ArrayList<>(topic.size());
        for (final LogTopic logTopic : topic) {
          position.add(logTopic == null ? null : Bytes32.wrap(logTopic.getBytes()));
        }
        transformedTopics.add(position);
      }
    }
    return transformedTopics;
  }

  /**
   * Estimates the gas used for a call execution.
   *
   * @param environment the DataFetchingEnvironment
   * @return the estimated gas used for the call execution
   */
  public Long getEstimateGas(final DataFetchingEnvironment environment) {
    final Optional<CallResult> result = executeCall(environment);
    return result.map(CallResult::getGasUsed).orElse(0L);
  }

  /**
   * Executes a call and returns the result.
   *
   * @param environment the DataFetchingEnvironment
   * @return an Optional containing the result of the call execution if it exists, otherwise an
   *     empty Optional
   */
  public Optional<CallResult> getCall(final DataFetchingEnvironment environment) {
    return executeCall(environment);
  }

  private Optional<CallResult> executeCall(final DataFetchingEnvironment environment) {
    return CallExecutor.executeCall(
        environment,
        header::getBlockHash,
        EnumSet.of(SimulationParameters.ALLOW_EXCEEDING_BALANCE));
  }

  /**
   * Returns the raw header of the current block.
   *
   * @param environment the DataFetchingEnvironment
   * @return the raw header of the block
   */
  Bytes getRawHeader(final DataFetchingEnvironment environment) {
    final RlpConverterService rlpConverterService =
        environment.getGraphQlContext().get(GraphQLContextType.RLP_CONVERTER_SERVICE);
    return rlpConverterService.buildRlpFromHeader(header);
  }

  /**
   * Returns the raw data of the current block.
   *
   * @param environment the DataFetchingEnvironment
   * @return the raw data of the block
   */
  Bytes getRaw(final DataFetchingEnvironment environment) {
    final ChainQueries query = getBlockchainQueries(environment);
    final RlpConverterService rlpConverterService =
        environment.getGraphQlContext().get(GraphQLContextType.RLP_CONVERTER_SERVICE);
    return query
        .getBlockBody(header.getBlockHash())
        .map(rlpConverterService::buildRlpFromBody)
        .orElse(Bytes.EMPTY);
  }

  /**
   * Returns the withdrawals root of the current block.
   *
   * @return an Optional containing the withdrawals root if it exists, otherwise an empty Optional
   */
  Optional<Hash> getWithdrawalsRoot() {
    return header.getWithdrawalsRoot().map(Hash.class::cast);
  }

  /**
   * Returns a list of withdrawals for the current block.
   *
   * @param environment the DataFetchingEnvironment
   * @return an Optional containing a list of WithdrawalAdapter instances representing the
   *     withdrawals of the current block if they exist, otherwise an empty Optional
   */
  Optional<List<WithdrawalAdapter>> getWithdrawals(final DataFetchingEnvironment environment) {
    final ChainQueries query = getBlockchainQueries(environment);
    return query
        .getBlockBody(header.getBlockHash())
        .flatMap(
            blockBody ->
                blockBody
                    .getWithdrawals()
                    .map(wl -> wl.stream().map(WithdrawalAdapter::new).toList()));
  }

  /**
   * Returns the blob gas used by the current block.
   *
   * @return an Optional containing the blob gas used by the current block if it exists, otherwise
   *     an empty Optional
   */
  public Optional<Long> getBlobGasUsed() {
    return header.getBlobGasUsed().map(Long.class::cast);
  }

  /**
   * Returns the excess blob gas of the current block.
   *
   * @return an Optional containing the excess blob gas of the current block if it exists, otherwise
   *     an empty Optional
   */
  public Optional<Long> getExcessBlobGas() {
    return header.getExcessBlobGas().map(BlockAdapterBase::toLong);
  }

  private static long toLong(final Quantity quantity) {
    if (quantity instanceof BlobGas blobGas) {
      return blobGas.toLong();
    }
    return quantity.getAsBigInteger().longValue();
  }
}
