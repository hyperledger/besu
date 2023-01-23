package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import io.vertx.core.Vertx;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.mainnet.TimestampSchedule;

public class EngineGetBlobsBundleV1 extends AbstractEngineGetPayload {

    public EngineGetBlobsBundleV1(
            final Vertx vertx,
            final ProtocolContext protocolContext,
            final MergeMiningCoordinator mergeMiningCoordinator,
            final BlockResultFactory blockResultFactory,
            final EngineCallListener engineCallListener,
            final TimestampSchedule schedule) {
        super(
                vertx,
                protocolContext,
                mergeMiningCoordinator,
                blockResultFactory,
                engineCallListener,
                schedule);
    }

    @Override
    protected JsonRpcResponse createResponse(JsonRpcRequestContext request, BlockWithReceipts blockWithReceipts) {
        return null;
    }

    @Override
    public String getName() {
        return RpcMethod.ENGINE_GET_BLOBS_BUNDLE_V1.getMethodName();
    }
}
