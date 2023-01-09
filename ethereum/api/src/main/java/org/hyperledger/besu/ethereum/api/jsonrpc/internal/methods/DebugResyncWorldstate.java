package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.Synchronizer;

public class DebugResyncWorldstate implements JsonRpcMethod {
  private final Synchronizer synchronizer;

  public DebugResyncWorldstate(final Synchronizer synchronizer) {
    this.synchronizer = synchronizer;
  }

  @Override
  public String getName() {
    return RpcMethod.DEBUG_RESYNC_WORLDSTATE.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext request) {
    return new JsonRpcSuccessResponse(
        request.getRequest().getId(), synchronizer.resyncWorldState());
  }
}
