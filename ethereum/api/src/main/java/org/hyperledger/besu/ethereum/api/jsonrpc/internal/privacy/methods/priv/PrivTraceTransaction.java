package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;


import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.PrivacyQueries;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

public class PrivTraceTransaction extends PrivateAbstractTraceByHash implements JsonRpcMethod {


    private final BlockchainQueries blockchainQueries;
    private final PrivacyQueries privacyQueries;
    private final PrivacyController privacyController;
    private final PrivacyIdProvider privacyIdProvider;

    public PrivTraceTransaction(BlockchainQueries blockchainQueries, PrivacyQueries privacyQueries, PrivacyController privacyController, PrivacyIdProvider privacyIdProvider) {
        this.blockchainQueries = blockchainQueries;
        this.privacyQueries = privacyQueries;
        this.privacyController = privacyController;
        this.privacyIdProvider = privacyIdProvider;
    }

    @Override
    public String getName() {
        return RpcMethod.PRIV_TRACE_TRANSACTION.getMethodName();
    }

    @Override
    public JsonRpcResponse response(JsonRpcRequestContext request) {
        return null;
    }
}
