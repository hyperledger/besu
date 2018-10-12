package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.UInt256Parameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.util.uint.UInt256;

public class EthGetStorageAt extends AbstractBlockParameterMethod {

  public EthGetStorageAt(final BlockchainQueries blockchain, final JsonRpcParameter parameters) {
    super(blockchain, parameters);
  }

  @Override
  public String getName() {
    return "eth_getStorageAt";
  }

  @Override
  protected BlockParameter blockParameter(final JsonRpcRequest request) {
    return parameters().required(request.getParams(), 2, BlockParameter.class);
  }

  @Override
  protected String resultByBlockNumber(final JsonRpcRequest request, final long blockNumber) {
    final Address address = parameters().required(request.getParams(), 0, Address.class);
    final UInt256 position =
        parameters().required(request.getParams(), 1, UInt256Parameter.class).getValue();
    return blockchainQueries()
        .storageAt(address, position, blockNumber)
        .map(UInt256::toHexString)
        .orElse(null);
  }
}
