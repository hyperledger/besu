package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.ExecutionEngineNewBlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class EngineExecutePayload extends ExecutionEngineJsonRpcMethod {

  private static final List<BlockHeader> OMMERS_CONSTANT = Collections.emptyList();
  private static final Hash OMMERS_HASH_CONSTANT = BodyValidation.ommersHash(OMMERS_CONSTANT);
  private static final Logger LOG = LogManager.getLogger();
  private static final BlockHeaderFunctions headerFunctions = new MainnetBlockHeaderFunctions();
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;

  public EngineExecutePayload(
      final Vertx vertx,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext) {
    super(vertx);
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_EXECUTE_PAYLOAD.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final ExecutionEngineNewBlockParameter blockParam =
        requestContext.getRequiredParameter(0, ExecutionEngineNewBlockParameter.class);

    try {
      LOG.trace("blockparam: " + Json.encodePrettily(blockParam));
    } catch (EncodeException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }

    final List<Transaction> transactions;
    try {
      transactions =
          blockParam.getTransactions().stream()
              .map(Bytes::fromHexString)
              .map(TransactionDecoder::decodeOpaqueBytes)
              .collect(Collectors.toList());
    } catch (final RLPException | IllegalArgumentException e) {
      LOG.warn("failed to decode transactions from newBlock RPC");
      e.printStackTrace();
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }

    final BlockHeader candidateBlockHeader =
        new BlockHeader(
            blockParam.getParentHash(),
            OMMERS_HASH_CONSTANT,
            blockParam.getCoinbase(),
            blockParam.getStateRoot(),
            BodyValidation.transactionsRoot(transactions),
            blockParam.getReceiptsRoot(),
            blockParam.getLogsBloom(),
            Difficulty.ONE,
            blockParam.getNumber(),
            blockParam.getGasLimit(),
            blockParam.getGasUsed(),
            blockParam.getTimestamp(),
            Bytes.EMPTY,
            blockParam.getBaseFeePerGas(),
            Hash.ZERO,
            0,
            headerFunctions);

    LOG.trace("candidateBlockHeader " + candidateBlockHeader);

    boolean blkHashEq = candidateBlockHeader.getHash().equals(blockParam.getBlockHash());
    LOG.trace("blkHashEq " + blkHashEq);

    ProtocolSpec spec = protocolSchedule.getByBlockNumber(candidateBlockHeader.getNumber());

    // TODO: we need to do more than just header validation
    //      https://github.com/ConsenSys/protocol-misc/issues/476
    if (!spec.getBlockHeaderValidator()
        .validateHeader(candidateBlockHeader, protocolContext, HeaderValidationMode.FULL)) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PAYLOAD);
    }

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
  }
}
