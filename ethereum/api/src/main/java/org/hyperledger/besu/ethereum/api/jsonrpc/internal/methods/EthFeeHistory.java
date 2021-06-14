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
  //
  //  private static class FeeHistory {
  //    private final long firstBlock;
  //    private final List<Long> baseFee;
  //    private final List<Double> gasUsedRatio;
  //
  //    private FeeHistory(
  //        final long firstBlock, final List<Long> baseFee, final List<Double> gasUsedRatio) {
  //      this.firstBlock = firstBlock;
  //      this.baseFee = baseFee;
  //      this.gasUsedRatio = gasUsedRatio;
  //    }
  //
  //    public long getFirstBlock() {
  //      return firstBlock;
  //    }
  //
  //    public List<Long> getBaseFee() {
  //      return baseFee;
  //    }
  //
  //    public List<Double> getGasUsedRatio() {
  //      return gasUsedRatio;
  //    }
  //  }
}
