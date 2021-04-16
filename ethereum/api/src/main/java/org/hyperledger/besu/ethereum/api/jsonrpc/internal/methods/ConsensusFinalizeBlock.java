package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

public class ConsensusFinalizeBlock implements JsonRpcMethod {
  @Override
  public String getName() {
    return RpcMethod.CONSENSUS_FINALIZE_BLOCK.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    // For now, just return success.
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), Boolean.TRUE);
  }
}
