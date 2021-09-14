package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import io.vertx.core.Vertx;

public class EnginePreparePayload extends ExecutionEngineJsonRpcMethod {
  public EnginePreparePayload(final Vertx vertx) {
    super(vertx);
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_PREPARE_PAYLOAD.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    // TODO: stubbed, implement https://github.com/ConsenSys/protocol-misc/issues/479
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
  }
}
