package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceTransaction;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor.PrivateBlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.PrivacyQueries;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivTraceTransaction extends PrivateAbstractTraceByHash implements JsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(TraceTransaction.class);

  public PrivTraceTransaction(
      final Supplier<PrivateBlockTracer> blockTracerSupplier,
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final PrivacyQueries privacyQueries,
      final PrivacyController privacyController,
      final PrivacyParameters privacyParameters,
      final PrivacyIdProvider privacyIdProvider) {
    super(
        blockTracerSupplier,
        blockchainQueries,
        privacyQueries,
        protocolSchedule,
        privacyController,
        privacyParameters,
        privacyIdProvider);
  }

  @Override
  public String getName() {
    return RpcMethod.PRIV_TRACE_TRANSACTION.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {

    final Hash transactionHash = requestContext.getRequiredParameter(1, Hash.class);
    LOG.trace("Received RPC rpcName={} txHash={}", getName(), transactionHash);

    return new JsonRpcSuccessResponse(
        requestContext.getRequest().getId(),
        arrayNodeFromTraceStream(resultByTransactionHash(transactionHash, requestContext)));
  }
}
