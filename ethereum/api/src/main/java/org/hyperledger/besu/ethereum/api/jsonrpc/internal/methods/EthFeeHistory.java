package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import static java.util.stream.Collectors.toUnmodifiableList;

public class EthFeeHistory implements JsonRpcMethod {
  private final BlockchainQueries blockchainQueries;
  private final Blockchain blockchain;

  public EthFeeHistory(final BlockchainQueries blockchainQueries) {
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

    final long resolvedBlockNumber =
        highestBlock
            .getNumber()
            .orElse(
                highestBlock.isEarliest()
                    ? BlockHeader.GENESIS_BLOCK_NUMBER
                    : blockchain
                        .getChainHeadBlockNumber() /* both latest and pending use the head block until we have pending block support */);
    final long firstBlock = Math.max(0, resolvedBlockNumber - (blockCount - 1));

    final List<Optional<Long>> baseFees =
        LongStream.range(firstBlock, firstBlock + blockCount)
            .mapToObj(blockchain::getBlockHeader)
            .map(maybeBlockHeader -> maybeBlockHeader.flatMap(ProcessableBlockHeader::getBaseFee))
            .collect(toUnmodifiableList());

    final List<Double> gasUsedRatios =
        LongStream.range(firstBlock, firstBlock + blockCount)
            .mapToObj(blockchain::getBlockHeader)
            .flatMap(
                maybeBlockheader ->
                    maybeBlockheader
                        .map(
                            blockHeader ->
                                blockHeader.getGasUsed() / (double) blockHeader.getGasLimit())
                        .stream())
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
        new FeeHistory(firstBlock, baseFees, gasUsedRatios, maybeRewards));
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
    for (int transactionIndex = 0; transactionIndex < transactions.size(); transactionIndex++) {

      while (rewardIndex < rewardPercentiles.size()
          && gasUsed / (double) block.getHeader().getGasUsed()
              > rewardPercentiles.get(rewardIndex)) {
        rewards.add(
            transactionsAscendingEffectiveGasFee
                .get(transactionIndex)
                .getEffectivePriorityFeePerGas(baseFee));
        rewardIndex++;
      }

      gasUsed +=
          blockchainQueries
              .transactionReceiptByTransactionHash(
                  transactionsAscendingEffectiveGasFee.get(transactionIndex).getHash())
              .get()
              .getGasUsed();
    }
    return rewards;
  }

  //  public FeeHistory feeHistory(final long blockCount, final long lastBlock) {
  //    return null;
  //  }

  public static class FeeHistory {
    private final long firstBlock;
    private final List<Optional<Long>> baseFees;
    private final List<Double> gasUsedRatios;
    private final Optional<List<List<Long>>> maybeRewards;

    //    FeeHistory(final long firstBlock, final List<Long> baseFees, final List<Double>
    // gasUsedRatios) {
    //      this(firstBlock, baseFees, gasUsedRatios, null);
    //    }

    public FeeHistory(
        final long firstBlock,
        final List<Optional<Long>> baseFees,
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

    public List<Optional<Long>> getBaseFees() {
      return baseFees;
    }

    public List<Double> getGasUsedRatios() {
      return gasUsedRatios;
    }

    @Override
    public String toString() {
      return String.format(
          "FeeHistory{firstBlock=%d, baseFees=%s, gasUsedRatios=%s, rewards=%s}",
          firstBlock, baseFees, gasUsedRatios, maybeRewards);
    }
  }
}
