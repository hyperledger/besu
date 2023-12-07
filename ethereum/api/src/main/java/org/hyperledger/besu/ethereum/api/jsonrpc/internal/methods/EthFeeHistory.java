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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static java.util.stream.Collectors.toUnmodifiableList;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.ApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.UnsignedIntParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.FeeHistory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.ImmutableFeeHistory;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;

public class EthFeeHistory implements JsonRpcMethod {
  private final ProtocolSchedule protocolSchedule;
  private final Blockchain blockchain;
  private final MiningCoordinator miningCoordinator;
  private final ApiConfiguration apiConfiguration;
  private final Cache<RewardCacheKey, List<Wei>> cache;
  private static final int MAXIMUM_CACHE_SIZE = 100_000;

  record RewardCacheKey(Hash blockHash, List<Double> rewardPercentiles) {}

  public EthFeeHistory(
      final ProtocolSchedule protocolSchedule,
      final Blockchain blockchain,
      final MiningCoordinator miningCoordinator,
      final ApiConfiguration apiConfiguration) {
    this.protocolSchedule = protocolSchedule;
    this.blockchain = blockchain;
    this.miningCoordinator = miningCoordinator;
    this.apiConfiguration = apiConfiguration;
    this.cache = Caffeine.newBuilder().maximumSize(MAXIMUM_CACHE_SIZE).build();
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_FEE_HISTORY.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext request) {
    final Object requestId = request.getRequest().getId();

    final int blockCount = request.getRequiredParameter(0, UnsignedIntParameter.class).getValue();
    if (isInvalidBlockCount(blockCount)) {
      return new JsonRpcErrorResponse(requestId, RpcErrorType.INVALID_PARAMS);
    }
    final BlockParameter highestBlock = request.getRequiredParameter(1, BlockParameter.class);
    final Optional<List<Double>> maybeRewardPercentiles =
        request.getOptionalParameter(2, Double[].class).map(Arrays::asList);

    final BlockHeader chainHeadHeader = blockchain.getChainHeadHeader();
    final long chainHeadBlockNumber = chainHeadHeader.getNumber();
    final long highestBlockNumber = highestBlock.getNumber().orElse(chainHeadBlockNumber);
    if (highestBlockNumber > chainHeadBlockNumber) {
      return new JsonRpcErrorResponse(requestId, RpcErrorType.INVALID_PARAMS);
    }

    final long firstBlock = Math.max(0, highestBlockNumber - (blockCount - 1));
    final long lastBlock =
        blockCount > highestBlockNumber ? (highestBlockNumber + 1) : (firstBlock + blockCount);

    final List<BlockHeader> blockHeaderRange = getBlockHeaders(firstBlock, lastBlock);
    final List<Wei> requestedBaseFees = getBaseFees(blockHeaderRange);
    final Wei nextBaseFee =
        getNextBaseFee(highestBlockNumber, chainHeadHeader, requestedBaseFees, blockHeaderRange);
    final List<Double> gasUsedRatios = getGasUsedRatios(blockHeaderRange);
    final Optional<List<List<Wei>>> maybeRewards =
        maybeRewardPercentiles.map(rewards -> getRewards(rewards, blockHeaderRange));
    return new JsonRpcSuccessResponse(
        requestId,
        createFeeHistoryResult(
            firstBlock, requestedBaseFees, nextBaseFee, gasUsedRatios, maybeRewards));
  }

  private Wei getNextBaseFee(
      final long resolvedHighestBlockNumber,
      final BlockHeader chainHeadHeader,
      final List<Wei> explicitlyRequestedBaseFees,
      final List<BlockHeader> blockHeaders) {
    final long nextBlockNumber = resolvedHighestBlockNumber + 1;
    return blockchain
        .getBlockHeader(nextBlockNumber)
        .map(blockHeader -> blockHeader.getBaseFee().orElse(Wei.ZERO))
        .orElseGet(
            () ->
                computeNextBaseFee(
                    nextBlockNumber, chainHeadHeader, explicitlyRequestedBaseFees, blockHeaders));
  }

  private Wei computeNextBaseFee(
      final long nextBlockNumber,
      final BlockHeader chainHeadHeader,
      final List<Wei> explicitlyRequestedBaseFees,
      final List<BlockHeader> blockHeaders) {

    // Note: We are able to use the chain head timestamp for next block header as
    // the base fee market can only be pre or post London. If another fee
    // market is added, we will need to reconsider this.

    // Get the fee market for the next block header
    Optional<FeeMarket> feeMarketOptional =
        Optional.of(
            protocolSchedule
                .getForNextBlockHeader(chainHeadHeader, chainHeadHeader.getTimestamp())
                .getFeeMarket());

    // If the fee market implements base fee, compute the next base fee
    return feeMarketOptional
        .filter(FeeMarket::implementsBaseFee)
        .map(BaseFeeMarket.class::cast)
        .map(
            feeMarket -> {
              // Get the last block header and the last explicitly requested base fee
              final BlockHeader lastBlockHeader = blockHeaders.get(blockHeaders.size() - 1);
              final Wei lastExplicitlyRequestedBaseFee =
                  explicitlyRequestedBaseFees.get(explicitlyRequestedBaseFees.size() - 1);

              // Compute the next base fee
              return feeMarket.computeBaseFee(
                  nextBlockNumber,
                  lastExplicitlyRequestedBaseFee,
                  lastBlockHeader.getGasUsed(),
                  feeMarket.targetGasUsed(lastBlockHeader));
            })
        .orElse(Wei.ZERO); // If the fee market does not implement base fee, return zero
  }

  private List<List<Wei>> getRewards(
      final List<Double> rewardPercentiles, final List<BlockHeader> blockHeaders) {
    var sortedPercentiles = rewardPercentiles.stream().sorted().toList();
    return blockHeaders.stream()
        .parallel()
        .map(blockHeader -> calculateBlockHeaderReward(sortedPercentiles, blockHeader))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<List<Wei>> calculateBlockHeaderReward(
      final List<Double> sortedPercentiles, final BlockHeader blockHeader) {

    // Create a new key for the reward cache
    final RewardCacheKey key = new RewardCacheKey(blockHeader.getBlockHash(), sortedPercentiles);

    // Try to get the rewards from the cache
    return Optional.ofNullable(cache.getIfPresent(key))
        .or(
            () -> {
              // If the rewards are not in the cache, compute them
              Optional<Block> block = blockchain.getBlockByHash(blockHeader.getBlockHash());
              return block.map(
                  b -> {
                    List<Wei> rewards = computeRewards(sortedPercentiles, b);
                    // Put the computed rewards in the cache for future use
                    cache.put(key, rewards);
                    return rewards;
                  });
            });
  }

  record TransactionInfo(Transaction transaction, Long gasUsed, Wei effectivePriorityFeePerGas) {}

  @VisibleForTesting
  public List<Wei> computeRewards(final List<Double> rewardPercentiles, final Block block) {
    final List<Transaction> transactions = block.getBody().getTransactions();
    if (transactions.isEmpty()) {
      // all 0's for empty block
      return generateZeroWeiList(rewardPercentiles.size());
    }
    final Optional<Wei> baseFee = block.getHeader().getBaseFee();
    final List<Long> transactionsGasUsed = calculateTransactionsGasUsed(block);
    final List<TransactionInfo> transactionsInfo =
        generateTransactionsInfo(transactions, transactionsGasUsed, baseFee);

    var realRewards = calculateRewards(rewardPercentiles, block, transactionsInfo);

    // If the priority fee boundary is set, return the bounded rewards. Otherwise, return the real
    // rewards.
    if (apiConfiguration.isPriorityFeeLimitingEnabled()) {
      return boundRewards(realRewards);
    } else {
      return realRewards;
    }
  }

  private List<Wei> calculateRewards(
      final List<Double> rewardPercentiles,
      final Block block,
      final List<TransactionInfo> sortedTransactionsInfo) {
    final ArrayList<Wei> rewards = new ArrayList<>(rewardPercentiles.size());

    // Start with the gas used by the first transaction
    double cumulativeGasUsed = sortedTransactionsInfo.get(0).gasUsed();
    var transactionIndex = 0;
    // Iterate over each reward percentile
    for (double rewardPercentile : rewardPercentiles) {
      // Calculate the threshold gas used for the current reward percentile
      // This is the amount of gas that needs to be used to reach this percentile
      var thresholdGasUsed = rewardPercentile * block.getHeader().getGasUsed() / 100;

      // Update cumulativeGasUsed by adding the gas used by each transaction
      // Stop when cumulativeGasUsed reaches the threshold or there are no more transactions
      while (cumulativeGasUsed < thresholdGasUsed
          && transactionIndex < sortedTransactionsInfo.size() - 1) {
        transactionIndex++;
        cumulativeGasUsed += sortedTransactionsInfo.get(transactionIndex).gasUsed();
      }
      // Add the effective priority fee per gas of the transaction that reached the percentile to
      // the rewards list
      rewards.add(sortedTransactionsInfo.get(transactionIndex).effectivePriorityFeePerGas);
    }
    return rewards;
  }

  /**
   * This method returns a list of bounded rewards.
   *
   * @param rewards The list of rewards to be bounded.
   * @return The list of bounded rewards.
   */
  private List<Wei> boundRewards(final List<Wei> rewards) {
    Wei minPriorityFee = miningCoordinator.getMinPriorityFeePerGas();
    Wei lowerBound =
        minPriorityFee.multiply(apiConfiguration.getLowerBoundPriorityFeeCoefficient()).divide(100);
    Wei upperBound =
        minPriorityFee.multiply(apiConfiguration.getUpperBoundPriorityFeeCoefficient()).divide(100);

    return rewards.stream().map(reward -> boundReward(reward, lowerBound, upperBound)).toList();
  }

  /**
   * This method bounds the reward between a lower and upper limit.
   *
   * @param reward The reward to be bounded.
   * @param lowerBound The lower limit for the reward.
   * @param upperBound The upper limit for the reward.
   * @return The bounded reward.
   */
  private Wei boundReward(final Wei reward, final Wei lowerBound, final Wei upperBound) {

    // If the reward is less than the lower bound, return the lower bound.
    if (reward.compareTo(lowerBound) <= 0) {
      return lowerBound;
    }

    // If the reward is greater than the upper bound, return the upper bound.
    if (reward.compareTo(upperBound) > 0) {
      return upperBound;
    }

    // If the reward is within the bounds, return the reward as is.
    return reward;
  }

  private List<Long> calculateTransactionsGasUsed(final Block block) {
    final List<Long> transactionsGasUsed = new ArrayList<>();
    long cumulativeGasUsed = 0L;
    for (final TransactionReceipt transactionReceipt :
        blockchain.getTxReceipts(block.getHash()).get()) {
      transactionsGasUsed.add(transactionReceipt.getCumulativeGasUsed() - cumulativeGasUsed);
      cumulativeGasUsed = transactionReceipt.getCumulativeGasUsed();
    }
    return transactionsGasUsed;
  }

  private List<TransactionInfo> generateTransactionsInfo(
      final List<Transaction> transactions,
      final List<Long> transactionsGasUsed,
      final Optional<Wei> baseFee) {
    return Streams.zip(
            transactions.stream(),
            transactionsGasUsed.stream(),
            (transaction, gasUsed) ->
                new TransactionInfo(
                    transaction, gasUsed, transaction.getEffectivePriorityFeePerGas(baseFee)))
        .sorted(Comparator.comparing(TransactionInfo::effectivePriorityFeePerGas))
        .toList();
  }

  private boolean isInvalidBlockCount(final int blockCount) {
    return blockCount < 1 || blockCount > 1024;
  }

  private List<BlockHeader> getBlockHeaders(final long oldestBlock, final long lastBlock) {
    return LongStream.range(oldestBlock, lastBlock)
        .parallel()
        .mapToObj(blockchain::getBlockHeader)
        .flatMap(Optional::stream)
        .toList();
  }

  private List<Wei> getBaseFees(final List<BlockHeader> blockHeaders) {
    // we return the base fees for the blocks requested and 1 more because we can always compute it
    return blockHeaders.stream()
        .map(blockHeader -> blockHeader.getBaseFee().orElse(Wei.ZERO))
        .toList();
  }

  private List<Double> getGasUsedRatios(final List<BlockHeader> blockHeaders) {
    return blockHeaders.stream()
        .map(blockHeader -> blockHeader.getGasUsed() / (double) blockHeader.getGasLimit())
        .toList();
  }

  private FeeHistory.FeeHistoryResult createFeeHistoryResult(
      final long oldestBlock,
      final List<Wei> explicitlyRequestedBaseFees,
      final Wei nextBaseFee,
      final List<Double> gasUsedRatios,
      final Optional<List<List<Wei>>> maybeRewards) {
    return FeeHistory.FeeHistoryResult.from(
        ImmutableFeeHistory.builder()
            .oldestBlock(oldestBlock)
            .baseFeePerGas(
                Stream.concat(explicitlyRequestedBaseFees.stream(), Stream.of(nextBaseFee))
                    .collect(toUnmodifiableList()))
            .gasUsedRatio(gasUsedRatios)
            .reward(maybeRewards)
            .build());
  }

  private List<Wei> generateZeroWeiList(final int size) {
    return Stream.generate(() -> Wei.ZERO).limit(size).toList();
  }
}
