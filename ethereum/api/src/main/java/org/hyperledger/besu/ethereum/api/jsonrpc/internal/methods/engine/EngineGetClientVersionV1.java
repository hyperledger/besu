package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import io.vertx.core.Vertx;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetClientVersionResultV1;

public class EngineGetClientVersionV1 extends ExecutionEngineJsonRpcMethod {
    private static final String ENGINE_CLIENT_CODE = "BU";
    private static final String ENGINE_CLIENT_NAME = "Besu";

    private final String clientVersion;
    private final String commit;

    public EngineGetClientVersionV1(final Vertx vertx, final ProtocolContext protocolContext, final EngineCallListener engineCallListener, final String clientVersion, final String commit) {
        super(vertx, protocolContext, engineCallListener);
        this.clientVersion = clientVersion;
        this.commit = commit;
    }

    @Override
    public String getName() {
        return RpcMethod.ENGINE_GET_CLIENT_VERSION_V1.getMethodName();
    }

    @Override
    public JsonRpcResponse syncResponse(final JsonRpcRequestContext request) {
        return new JsonRpcSuccessResponse(request.getRequest().getId(), new EngineGetClientVersionResultV1(ENGINE_CLIENT_CODE, ENGINE_CLIENT_NAME, clientVersion, commit));
    }
}
