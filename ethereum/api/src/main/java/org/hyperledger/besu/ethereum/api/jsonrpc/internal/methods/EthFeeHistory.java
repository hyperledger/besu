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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class EthFeeHistory implements JsonRpcMethod {
  private final ProtocolSchedule protocolSchedule;
  private final BlockchainQueries blockchainQueries;
  private final Blockchain blockchain;

  public EthFeeHistory(
      final ProtocolSchedule protocolSchedule, final BlockchainQueries blockchainQueries) {
    this.protocolSchedule = protocolSchedule;
    this.blockchainQueries = blockchainQueries;
    this.blockchain = blockchainQueries.getBlockchain();
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_FEE_HISTORY.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext request) {
    final long blockCount = request.getRequiredParameter(0, Long.class);
    final BlockParameter highestBlock = request.getRequiredParameter(1, BlockParameter.class);
    final Optional<List<Double>> maybeRewardPercentiles =
        request.getOptionalParameter(2, Double[].class).map(Arrays::asList);

    final long resolvedHighestBlockNumber =
        highestBlock
            .getNumber()
            .orElse(
                highestBlock.isEarliest()
                    ? BlockHeader.GENESIS_BLOCK_NUMBER
                    : blockchain
                        .getChainHeadBlockNumber() /* both latest and pending use the head block until we have pending block support */);
    final long firstBlock = Math.max(0, resolvedHighestBlockNumber - (blockCount - 1));

    final List<BlockHeader> blockHeaders =
        LongStream.range(firstBlock, firstBlock + blockCount)
            .mapToObj(blockchain::getBlockHeader)
            .flatMap(Optional::stream)
            .collect(toUnmodifiableList());

    // we return the base fees for the blocks requested and 1 more because we can always compute it
    final List<Long> explicitlyRequestedBaseFees =
        LongStream.range(firstBlock, firstBlock + blockCount)
            .mapToObj(blockchain::getBlockHeader)
            .map(
                maybeBlockHeader ->
                    maybeBlockHeader.flatMap(ProcessableBlockHeader::getBaseFee).orElse(0L))
            .collect(toUnmodifiableList());
    final long nextBlockNumber = resolvedHighestBlockNumber + 1;
    final Long nextBaseFee =
        blockchain
            .getBlockHeader(nextBlockNumber)
            .map(blockHeader -> blockHeader.getBaseFee().orElse(0L))
            .orElseGet(
                () ->
                    protocolSchedule
                        .getByBlockNumber(nextBlockNumber)
                        .getEip1559()
                        .map(
                            eip1559 -> {
                              final BlockHeader lastBlockHeader =
                                  blockHeaders.get(blockHeaders.size() - 1);
                              return eip1559.computeBaseFee(
                                  nextBlockNumber,
                                  explicitlyRequestedBaseFees.get(
                                      explicitlyRequestedBaseFees.size() - 1),
                                  lastBlockHeader.getGasUsed(),
                                  eip1559.targetGasUsed(lastBlockHeader));
                            })
                        .orElse(0L));

    final List<Double> gasUsedRatios =
        blockHeaders.stream()
            .map(blockHeader -> blockHeader.getGasUsed() / (double) blockHeader.getGasLimit())
            .collect(toUnmodifiableList());

    final Optional<List<List<Long>>> maybeRewards =
        maybeRewardPercentiles.map(
            rewardPercentiles ->
                LongStream.range(firstBlock, firstBlock + blockCount)
                    .mapToObj(blockNumber -> blockchain.getBlockByNumber(blockNumber).get())
                    .map(block -> computeRewards(rewardPercentiles, block))
                    .collect(toUnmodifiableList()));

    return new JsonRpcSuccessResponse(
        request.getRequest().getId(),
        new FeeHistory(
            firstBlock,
            Stream.concat(explicitlyRequestedBaseFees.stream(), Stream.of(nextBaseFee))
                .collect(toUnmodifiableList()),
            gasUsedRatios,
            maybeRewards));
  }

  private List<Long> computeRewards(
      final List<Double> rewardPercentiles, final org.hyperledger.besu.ethereum.core.Block block) {
    final List<Transaction> transactions = block.getBody().getTransactions();
    if (transactions.isEmpty()) {
      // all 0's for empty block
      return LongStream.generate(() -> 0)
          .limit(rewardPercentiles.size())
          .boxed()
          .collect(toUnmodifiableList());
    }

    final Optional<Long> baseFee = block.getHeader().getBaseFee();
    final List<Transaction> transactionsAscendingEffectiveGasFee =
        transactions.stream()
            .sorted(
                Comparator.comparing(
                    transaction -> transaction.getEffectivePriorityFeePerGas(baseFee)))
            .collect(toUnmodifiableList());

    final ArrayList<Long> rewards = new ArrayList<>();
    int rewardIndex = 0;
    long gasUsed = 0;
    for (final Transaction transaction : transactionsAscendingEffectiveGasFee) {

      gasUsed +=
          blockchainQueries
              .transactionReceiptByTransactionHash(transaction.getHash())
              .get()
              .getGasUsed();

      while (rewardIndex < rewardPercentiles.size()
          && 100.0 * gasUsed / block.getHeader().getGasUsed()
              >= rewardPercentiles.get(rewardIndex)) {
        rewards.add(transaction.getEffectivePriorityFeePerGas(baseFee));
        rewardIndex++;
      }
    }
    return rewards;
  }

  public static class FeeHistory {
    private final long firstBlock;
    private final List<Long> baseFees;
    private final List<Double> gasUsedRatios;
    private final Optional<List<List<Long>>> maybeRewards;

    public FeeHistory(
        final long firstBlock,
        final List<Long> baseFees,
        final List<Double> gasUsedRatios,
        final Optional<List<List<Long>>> maybeRewards) {
      this.firstBlock = firstBlock;
      this.baseFees = baseFees;
      this.gasUsedRatios = gasUsedRatios;
      this.maybeRewards = maybeRewards;
    }

    public long getFirstBlock() {
      return firstBlock;
    }

    public List<Long> getBaseFees() {
      return baseFees;
    }

    public List<Double> getGasUsedRatios() {
      return gasUsedRatios;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final FeeHistory that = (FeeHistory) o;
      return firstBlock == that.firstBlock
          && baseFees.equals(that.baseFees)
          && gasUsedRatios.equals(that.gasUsedRatios)
          && maybeRewards.equals(that.maybeRewards);
    }

    @Override
    public int hashCode() {
      return Objects.hash(firstBlock, baseFees, gasUsedRatios, maybeRewards);
    }

    @Override
    public String toString() {
      return String.format(
          "FeeHistory{firstBlock=%d, baseFees=%s, gasUsedRatios=%s, rewards=%s}",
          firstBlock, baseFees, gasUsedRatios, maybeRewards);
    }
  }
}
