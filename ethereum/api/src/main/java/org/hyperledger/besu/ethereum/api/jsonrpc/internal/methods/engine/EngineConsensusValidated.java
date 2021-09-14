package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import io.vertx.core.Vertx;

public class EngineConsensusValidated extends ExecutionEngineJsonRpcMethod {

  public EngineConsensusValidated(final Vertx vertx) {
    super(vertx);
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_CONSENSUS_VALIDATED.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    // final Hash blockHash = requestContext.getRequiredParameter(0, Hash.class);

    // TODO: implement me https://github.com/ConsenSys/protocol-misc/issues/477
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
  }
}
