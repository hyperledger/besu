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

import java.util.Arrays;
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
    final Optional<List<Double>> rewardPercentiles =
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

    return new JsonRpcSuccessResponse(
        request.getRequest().getId(),
        new FeeHistory(
            firstBlock,
            LongStream.range(firstBlock, firstBlock + blockCount)
                .mapToObj(blockchain::getBlockHeader)
                .map(
                    maybeBlockHeader ->
                        maybeBlockHeader.flatMap(ProcessableBlockHeader::getBaseFee))
                .collect(toUnmodifiableList()),
            LongStream.range(firstBlock, firstBlock + blockCount)
                .mapToObj(blockchain::getBlockHeader)
                .flatMap(
                    maybeBlockheader ->
                        maybeBlockheader
                            .map(
                                blockHeader ->
                                    blockHeader.getGasUsed() / (double) blockHeader.getGasLimit())
                            .stream())
                .collect(toUnmodifiableList()),
            null));
  }

  //  public FeeHistory feeHistory(final long blockCount, final long lastBlock) {
  //    return null;
  //  }

  public static class FeeHistory {
    private final long firstBlock;
    private final List<Optional<Long>> baseFees;
    private final List<Double> gasUsedRatios;
    private final Optional<List<List<Long>>> rewards;

    //    FeeHistory(final long firstBlock, final List<Long> baseFees, final List<Double>
    // gasUsedRatios) {
    //      this(firstBlock, baseFees, gasUsedRatios, null);
    //    }

    public FeeHistory(
        final long firstBlock,
        final List<Optional<Long>> baseFees,
        final List<Double> gasUsedRatios,
        final List<List<Long>> rewards) {
      this.firstBlock = firstBlock;
      this.baseFees = baseFees;
      this.gasUsedRatios = gasUsedRatios;
      this.rewards = Optional.ofNullable(rewards);
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
          firstBlock, baseFees, gasUsedRatios, rewards);
    }
  }
}
