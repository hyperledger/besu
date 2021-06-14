package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class EthFeeHistory implements JsonRpcMethod {
  private final BlockchainQueries blockchainQueries;

  public EthFeeHistory(final BlockchainQueries blockchainQueries) {
    this.blockchainQueries = blockchainQueries;
  }

  @Override
  public String getName() {
    return RpcMethod.ETH_FEE_HISTORY.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext request) {
    final long blockCount = request.getRequiredParameter(0, Long.class);
    final BlockParameter blockParameter = request.getRequiredParameter(1, BlockParameter.class);
    final Optional<List<Double>> rewardPercentiles =
        request.getOptionalParameter(2, Double[].class).map(Arrays::asList);
    return null;
  }

  //  public FeeHistory feeHistory(final long blockCount, final long lastBlock) {
  //    return null;
  //  }

  public static class FeeHistory {
    private final long firstBlock;
    private final List<Long> baseFees;
    private final List<Double> gasUsedRatios;
    private final Optional<List<List<Long>>> rewards;

    FeeHistory(final long firstBlock, final List<Long> baseFees, final List<Double> gasUsedRatios) {
      this(firstBlock, baseFees, gasUsedRatios, null);
    }

    public FeeHistory(
        final long firstBlock,
        final List<Long> baseFees,
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

    public List<Long> getBaseFees() {
      return baseFees;
    }

    public List<Double> getGasUsedRatios() {
      return gasUsedRatios;
    }
  }
}
