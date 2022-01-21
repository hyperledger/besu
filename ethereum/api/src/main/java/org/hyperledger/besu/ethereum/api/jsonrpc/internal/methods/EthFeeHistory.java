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

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.UnsignedLongParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.FeeHistory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.ImmutableFeeHistory;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import com.google.common.collect.Streams;

public class EthFeeHistory implements JsonRpcMethod {
  private final ProtocolSchedule protocolSchedule;
  private final Blockchain blockchain;

  public EthFeeHistory(final ProtocolSchedule protocolSchedule, final Blockchain blockchain) {
    this.protocolSchedule = protocolSchedule;
    this.blockchain = blockchain;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_FEE_HISTORY.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext request) {
    final Object requestId = request.getRequest().getId();

    final long blockCount =
        Optional.of(request.getRequiredParameter(0, UnsignedLongParameter.class))
            .map(UnsignedLongParameter::getValue)
            .orElse(0L);

    if (blockCount < 1 || blockCount > 1024) {
      return new JsonRpcErrorResponse(requestId, JsonRpcError.INVALID_PARAMS);
    }
    final BlockParameter highestBlock = request.getRequiredParameter(1, BlockParameter.class);
    final Optional<List<Double>> maybeRewardPercentiles =
        request.getOptionalParameter(2, Double[].class).map(Arrays::asList);

    final long chainHeadBlockNumber = blockchain.getChainHeadBlockNumber();
    final long resolvedHighestBlockNumber =
        highestBlock
            .getNumber()
            .orElse(
                chainHeadBlockNumber /* both latest and pending use the head block until we have pending block support */);

    if (resolvedHighestBlockNumber > chainHeadBlockNumber) {
      return new JsonRpcErrorResponse(requestId, JsonRpcError.INVALID_PARAMS);
    }

    final long oldestBlock = Math.max(0, resolvedHighestBlockNumber - (blockCount - 1));

    final List<BlockHeader> blockHeaders =
        LongStream.range(oldestBlock, oldestBlock + blockCount)
            .mapToObj(blockchain::getBlockHeader)
            .flatMap(Optional::stream)
            .collect(toUnmodifiableList());

    // we return the base fees for the blocks requested and 1 more because we can always compute it
    final List<Wei> explicitlyRequestedBaseFees =
        blockHeaders.stream()
            .map(blockHeader -> blockHeader.getBaseFee().orElse(Wei.ZERO))
            .collect(toUnmodifiableList());
    final long nextBlockNumber = resolvedHighestBlockNumber + 1;
    final Wei nextBaseFee =
        blockchain
            .getBlockHeader(nextBlockNumber)
            .map(blockHeader -> blockHeader.getBaseFee().orElse(Wei.ZERO))
            .orElseGet(
                () ->
                    Optional.of(protocolSchedule.getByBlockNumber(nextBlockNumber).getFeeMarket())
                        .filter(FeeMarket::implementsBaseFee)
                        .map(BaseFeeMarket.class::cast)
                        .map(
                            feeMarket -> {
                              final BlockHeader lastBlockHeader =
                                  blockHeaders.get(blockHeaders.size() - 1);
                              return feeMarket.computeBaseFee(
                                  nextBlockNumber,
                                  explicitlyRequestedBaseFees.get(
                                      explicitlyRequestedBaseFees.size() - 1),
                                  lastBlockHeader.getGasUsed(),
                                  feeMarket.targetGasUsed(lastBlockHeader));
                            })
                        .orElse(Wei.ZERO));

    final List<Double> gasUsedRatios =
        blockHeaders.stream()
            .map(blockHeader -> blockHeader.getGasUsed() / (double) blockHeader.getGasLimit())
            .collect(toUnmodifiableList());

    final Optional<List<List<Wei>>> maybeRewards =
        maybeRewardPercentiles.map(
            rewardPercentiles ->
                LongStream.range(oldestBlock, oldestBlock + blockCount)
                    .mapToObj(blockchain::getBlockByNumber)
                    .flatMap(Optional::stream)
                    .map(
                        block ->
                            computeRewards(
                                rewardPercentiles.stream().sorted().collect(toUnmodifiableList()),
                                block))
                    .collect(toUnmodifiableList()));

    return new JsonRpcSuccessResponse(
        requestId,
        FeeHistory.FeeHistoryResult.from(
            ImmutableFeeHistory.builder()
                .oldestBlock(oldestBlock)
                .baseFeePerGas(
                    Stream.concat(explicitlyRequestedBaseFees.stream(), Stream.of(nextBaseFee))
                        .collect(toUnmodifiableList()))
                .gasUsedRatio(gasUsedRatios)
                .reward(maybeRewards)
                .build()));
  }

  private List<Wei> computeRewards(final List<Double> rewardPercentiles, final Block block) {
    final List<Transaction> transactions = block.getBody().getTransactions();
    if (transactions.isEmpty()) {
      // all 0's for empty block
      return Stream.generate(() -> Wei.ZERO)
          .limit(rewardPercentiles.size())
          .collect(toUnmodifiableList());
    }

    final Optional<Wei> baseFee = block.getHeader().getBaseFee();

    // we need to get the gas used for the individual transactions and can't use the cumulative gas
    // used because we're going to be reordering the transactions
    final List<Long> transactionsGasUsed = new ArrayList<>();
    for (final TransactionReceipt transactionReceipt :
        blockchain.getTxReceipts(block.getHash()).get()) {
      transactionsGasUsed.add(
          transactionsGasUsed.isEmpty()
              ? transactionReceipt.getCumulativeGasUsed()
              : transactionReceipt.getCumulativeGasUsed()
                  - transactionsGasUsed.get(transactionsGasUsed.size() - 1));
    }
    final List<Map.Entry<Transaction, Long>> transactionsAndGasUsedAscendingEffectiveGasFee =
        Streams.zip(
                transactions.stream(), transactionsGasUsed.stream(), AbstractMap.SimpleEntry::new)
            .sorted(
                Comparator.comparing(
                    transactionAndGasUsed ->
                        transactionAndGasUsed.getKey().getEffectivePriorityFeePerGas(baseFee)))
            .collect(toUnmodifiableList());

    // We need to weight the percentile of rewards by the gas used in the transaction.
    // That's why we're keeping track of the cumulative gas used and checking to see which
    // percentile markers we've passed
    final ArrayList<Wei> rewards = new ArrayList<>();
    int rewardPercentileIndex = 0;
    long gasUsed = 0;
    for (final Map.Entry<Transaction, Long> transactionAndGasUsed :
        transactionsAndGasUsedAscendingEffectiveGasFee) {

      gasUsed += transactionAndGasUsed.getValue();

      while (rewardPercentileIndex < rewardPercentiles.size()
          && 100.0 * gasUsed / block.getHeader().getGasUsed()
              >= rewardPercentiles.get(rewardPercentileIndex)) {
        rewards.add(transactionAndGasUsed.getKey().getEffectivePriorityFeePerGas(baseFee));
        rewardPercentileIndex++;
      }
    }
    return rewards;
  }
}
