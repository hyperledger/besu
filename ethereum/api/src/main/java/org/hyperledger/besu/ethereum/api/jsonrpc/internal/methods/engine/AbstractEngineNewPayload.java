/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static java.util.stream.Collectors.toList;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.ACCEPTED;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.VALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.DepositsValidatorProvider.getDepositsValidator;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine.WithdrawalsValidatorProvider.getWithdrawalsValidator;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType.INVALID_PARAMS;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.DepositParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.WithdrawalParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EnginePayloadStatusResult;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.core.encoding.EncodingContext;
import org.hyperledger.besu.ethereum.core.encoding.TransactionDecoder;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessBlobGasCalculator;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractEngineNewPayload extends ExecutionEngineJsonRpcMethod {

  private static final Hash OMMERS_HASH_CONSTANT = Hash.EMPTY_LIST_HASH;
  private static final Logger LOG = LoggerFactory.getLogger(AbstractEngineNewPayload.class);
  private static final BlockHeaderFunctions headerFunctions = new MainnetBlockHeaderFunctions();
  private final MergeMiningCoordinator mergeCoordinator;
  private final EthPeers ethPeers;

  public AbstractEngineNewPayload(
      final Vertx vertx,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EthPeers ethPeers,
      final EngineCallListener engineCallListener) {
    super(vertx, protocolSchedule, protocolContext, engineCallListener);
    this.mergeCoordinator = mergeCoordinator;
    this.ethPeers = ethPeers;
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    engineCallListener.executionEngineCalled();

    final EnginePayloadParameter blockParam =
        requestContext.getRequiredParameter(0, EnginePayloadParameter.class);

    final Optional<List<String>> maybeVersionedHashParam =
        requestContext.getOptionalList(1, String.class);

    final Object reqId = requestContext.getRequest().getId();

    Optional<String> maybeParentBeaconBlockRootParam =
        requestContext.getOptionalParameter(2, String.class);
    final Optional<Bytes32> maybeParentBeaconBlockRoot =
        maybeParentBeaconBlockRootParam.map(Bytes32::fromHexString);

    final ValidationResult<RpcErrorType> parameterValidationResult =
        validateParameters(blockParam, maybeVersionedHashParam, maybeParentBeaconBlockRootParam);
    if (!parameterValidationResult.isValid()) {
      return new JsonRpcErrorResponse(reqId, parameterValidationResult);
    }

    final ValidationResult<RpcErrorType> forkValidationResult =
        validateForkSupported(blockParam.getTimestamp());
    if (!forkValidationResult.isValid()) {
      return new JsonRpcErrorResponse(reqId, forkValidationResult);
    }

    final Optional<List<VersionedHash>> maybeVersionedHashes;
    try {
      maybeVersionedHashes = extractVersionedHashes(maybeVersionedHashParam);
    } catch (RuntimeException ex) {
      return respondWithInvalid(
          reqId,
          blockParam,
          mergeCoordinator.getLatestValidAncestor(blockParam.getParentHash()).orElse(null),
          INVALID,
          "Invalid versionedHash");
    }

    final Optional<BlockHeader> maybeParentHeader =
        protocolContext.getBlockchain().getBlockHeader(blockParam.getParentHash());

    LOG.atTrace()
        .setMessage("blockparam: {}")
        .addArgument(() -> Json.encodePrettily(blockParam))
        .log();

    final Optional<List<Withdrawal>> maybeWithdrawals =
        Optional.ofNullable(blockParam.getWithdrawals())
            .map(ws -> ws.stream().map(WithdrawalParameter::toWithdrawal).collect(toList()));

    if (!getWithdrawalsValidator(
            protocolSchedule.get(), blockParam.getTimestamp(), blockParam.getBlockNumber())
        .validateWithdrawals(maybeWithdrawals)) {
      return new JsonRpcErrorResponse(
          reqId, new JsonRpcError(INVALID_PARAMS, "Invalid withdrawals"));
    }

    final Optional<List<Deposit>> maybeDeposits =
        Optional.ofNullable(blockParam.getDeposits())
            .map(ds -> ds.stream().map(DepositParameter::toDeposit).collect(toList()));
    if (!getDepositsValidator(
            protocolSchedule.get(), blockParam.getTimestamp(), blockParam.getBlockNumber())
        .validateDepositParameter(maybeDeposits)) {
      return new JsonRpcErrorResponse(reqId, new JsonRpcError(INVALID_PARAMS, "Invalid deposits"));
    }

    if (mergeContext.get().isSyncing()) {
      LOG.debug("We are syncing");
      return respondWith(reqId, blockParam, null, SYNCING);
    }

    final List<Transaction> transactions;
    try {
      transactions =
          blockParam.getTransactions().stream()
              .map(Bytes::fromHexString)
              .map(in -> TransactionDecoder.decodeOpaqueBytes(in, EncodingContext.BLOCK_BODY))
              .collect(Collectors.toList());
    } catch (final RLPException | IllegalArgumentException e) {
      return respondWithInvalid(
          reqId,
          blockParam,
          mergeCoordinator.getLatestValidAncestor(blockParam.getParentHash()).orElse(null),
          INVALID,
          "Failed to decode transactions from block parameter");
    }

    if (blockParam.getExtraData() == null) {
      return respondWithInvalid(
          reqId,
          blockParam,
          mergeCoordinator.getLatestValidAncestor(blockParam.getParentHash()).orElse(null),
          INVALID,
          "Field extraData must not be null");
    }

    final BlockHeader newBlockHeader =
        new BlockHeader(
            blockParam.getParentHash(),
            OMMERS_HASH_CONSTANT,
            blockParam.getFeeRecipient(),
            blockParam.getStateRoot(),
            BodyValidation.transactionsRoot(transactions),
            blockParam.getReceiptsRoot(),
            blockParam.getLogsBloom(),
            Difficulty.ZERO,
            blockParam.getBlockNumber(),
            blockParam.getGasLimit(),
            blockParam.getGasUsed(),
            blockParam.getTimestamp(),
            Bytes.fromHexString(blockParam.getExtraData()),
            blockParam.getBaseFeePerGas(),
            blockParam.getPrevRandao(),
            0,
            maybeWithdrawals.map(BodyValidation::withdrawalsRoot).orElse(null),
            blockParam.getBlobGasUsed(),
            blockParam.getExcessBlobGas() == null
                ? null
                : BlobGas.fromHexString(blockParam.getExcessBlobGas()),
            maybeParentBeaconBlockRoot.orElse(null),
            maybeDeposits.map(BodyValidation::depositsRoot).orElse(null),
            headerFunctions);

    // ensure the block hash matches the blockParam hash
    // this must be done before any other check
    if (!newBlockHeader.getHash().equals(blockParam.getBlockHash())) {
      String errorMessage =
          String.format(
              "Computed block hash %s does not match block hash parameter %s",
              newBlockHeader.getBlockHash(), blockParam.getBlockHash());
      LOG.debug(errorMessage);
      return respondWithInvalid(reqId, blockParam, null, getInvalidBlockHashStatus(), errorMessage);
    }

    ValidationResult<RpcErrorType> blobValidationResult =
        validateBlobs(
            transactions,
            newBlockHeader,
            maybeParentHeader,
            maybeVersionedHashes,
            protocolSchedule.get().getByBlockHeader(newBlockHeader));
    if (!blobValidationResult.isValid()) {
      return respondWithInvalid(
          reqId,
          blockParam,
          mergeCoordinator.getLatestValidAncestor(blockParam.getParentHash()).orElse(null),
          getInvalidBlockHashStatus(),
          blobValidationResult.getErrorMessage());
    }

    // do we already have this payload
    if (protocolContext.getBlockchain().getBlockByHash(newBlockHeader.getBlockHash()).isPresent()) {
      LOG.debug("block already present");
      return respondWith(reqId, blockParam, blockParam.getBlockHash(), VALID);
    }
    if (mergeCoordinator.isBadBlock(blockParam.getBlockHash())) {
      return respondWithInvalid(
          reqId,
          blockParam,
          mergeCoordinator
              .getLatestValidHashOfBadBlock(blockParam.getBlockHash())
              .orElse(Hash.ZERO),
          INVALID,
          "Block already present in bad block manager.");
    }

    if (maybeParentHeader.isPresent()
        && (blockParam.getTimestamp() <= maybeParentHeader.get().getTimestamp())) {
      return respondWithInvalid(
          reqId,
          blockParam,
          mergeCoordinator.getLatestValidAncestor(blockParam.getParentHash()).orElse(null),
          INVALID,
          "block timestamp not greater than parent");
    }

    final var block =
        new Block(
            newBlockHeader,
            new BlockBody(transactions, Collections.emptyList(), maybeWithdrawals, maybeDeposits));

    if (maybeParentHeader.isEmpty()) {
      LOG.atDebug()
          .setMessage("Parent of block {} is not present, append it to backward sync")
          .addArgument(block::toLogString)
          .log();
      mergeCoordinator.appendNewPayloadToSync(block);
      return respondWith(reqId, blockParam, null, SYNCING);
    }

    final var latestValidAncestor = mergeCoordinator.getLatestValidAncestor(newBlockHeader);

    if (latestValidAncestor.isEmpty()) {
      return respondWith(reqId, blockParam, null, ACCEPTED);
    }

    // execute block and return result response
    final long startTimeMs = System.currentTimeMillis();
    final BlockProcessingResult executionResult = mergeCoordinator.rememberBlock(block);

    if (executionResult.isSuccessful()) {
      logImportedBlockInfo(block, (System.currentTimeMillis() - startTimeMs) / 1000.0);
      return respondWith(reqId, blockParam, newBlockHeader.getHash(), VALID);
    } else {
      if (executionResult.causedBy().isPresent()) {
        Throwable causedBy = executionResult.causedBy().get();
        if (causedBy instanceof StorageException || causedBy instanceof MerkleTrieException) {
          RpcErrorType error = RpcErrorType.INTERNAL_ERROR;
          JsonRpcErrorResponse response = new JsonRpcErrorResponse(reqId, error);
          return response;
        }
      }
      LOG.debug("New payload is invalid: {}", executionResult.errorMessage.get());
      return respondWithInvalid(
          reqId,
          blockParam,
          latestValidAncestor.get(),
          INVALID,
          executionResult.errorMessage.get());
    }
  }

  JsonRpcResponse respondWith(
      final Object requestId,
      final EnginePayloadParameter param,
      final Hash latestValidHash,
      final EngineStatus status) {
    if (INVALID.equals(status) || INVALID_BLOCK_HASH.equals(status)) {
      throw new IllegalArgumentException(
          "Don't call respondWith() with invalid status of " + status.toString());
    }
    LOG.atDebug()
        .setMessage(
            "New payload: number: {}, hash: {}, parentHash: {}, latestValidHash: {}, status: {}")
        .addArgument(param::getBlockNumber)
        .addArgument(param::getBlockHash)
        .addArgument(param::getParentHash)
        .addArgument(() -> latestValidHash == null ? null : latestValidHash.toHexString())
        .addArgument(status::name)
        .log();
    return new JsonRpcSuccessResponse(
        requestId, new EnginePayloadStatusResult(status, latestValidHash, Optional.empty()));
  }

  // engine api calls are synchronous, no need for volatile
  private long lastInvalidWarn = 0;

  JsonRpcResponse respondWithInvalid(
      final Object requestId,
      final EnginePayloadParameter param,
      final Hash latestValidHash,
      final EngineStatus invalidStatus,
      final String validationError) {
    if (!INVALID.equals(invalidStatus) && !INVALID_BLOCK_HASH.equals(invalidStatus)) {
      throw new IllegalArgumentException(
          "Don't call respondWithInvalid() with non-invalid status of " + invalidStatus.toString());
    }
    final String invalidBlockLogMessage =
        String.format(
            "Invalid new payload: number: %s, hash: %s, parentHash: %s, latestValidHash: %s, status: %s, validationError: %s",
            param.getBlockNumber(),
            param.getBlockHash(),
            param.getParentHash(),
            latestValidHash == null ? null : latestValidHash.toHexString(),
            invalidStatus.name(),
            validationError);
    // always log invalid at DEBUG
    LOG.debug(invalidBlockLogMessage);
    // periodically log at WARN
    if (lastInvalidWarn + ENGINE_API_LOGGING_THRESHOLD < System.currentTimeMillis()) {
      lastInvalidWarn = System.currentTimeMillis();
      LOG.warn(invalidBlockLogMessage);
    }
    return new JsonRpcSuccessResponse(
        requestId,
        new EnginePayloadStatusResult(
            invalidStatus, latestValidHash, Optional.of(validationError)));
  }

  protected boolean requireTerminalPoWBlockValidation() {
    return false;
  }

  protected EngineStatus getInvalidBlockHashStatus() {
    return INVALID;
  }

  protected ValidationResult<RpcErrorType> validateParameters(
      final EnginePayloadParameter parameter,
      final Optional<List<String>> maybeVersionedHashParam,
      final Optional<String> maybeBeaconBlockRootParam) {
    return ValidationResult.valid();
  }

  protected ValidationResult<RpcErrorType> validateBlobs(
      final List<Transaction> transactions,
      final BlockHeader header,
      final Optional<BlockHeader> maybeParentHeader,
      final Optional<List<VersionedHash>> maybeVersionedHashes,
      final ProtocolSpec protocolSpec) {

    var blobTransactions =
        transactions.stream().filter(transaction -> transaction.getType().supportsBlob()).toList();

    final List<VersionedHash> transactionVersionedHashes = new ArrayList<>();
    for (Transaction transaction : blobTransactions) {
      var versionedHashes = transaction.getVersionedHashes();
      // blob transactions must have at least one blob
      if (versionedHashes.isEmpty()) {
        return ValidationResult.invalid(
            RpcErrorType.INVALID_PARAMS, "There must be at least one blob");
      }
      transactionVersionedHashes.addAll(versionedHashes.get());
    }

    if (maybeVersionedHashes.isEmpty() && !transactionVersionedHashes.isEmpty()) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_PARAMS, "Payload must contain versioned hashes for transactions");
    }

    // Validate versionedHashesParam
    if (maybeVersionedHashes.isPresent()
        && !maybeVersionedHashes.get().equals(transactionVersionedHashes)) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_PARAMS,
          "Versioned hashes from blob transactions do not match expected values");
    }

    // Validate excessBlobGas
    if (maybeParentHeader.isPresent()) {
      if (!validateExcessBlobGas(header, maybeParentHeader.get(), protocolSpec)) {
        return ValidationResult.invalid(
            RpcErrorType.INVALID_PARAMS,
            "Payload excessBlobGas does not match calculated excessBlobGas");
      }
    }

    // Validate blobGasUsed
    if (header.getBlobGasUsed().isPresent() && maybeVersionedHashes.isPresent()) {
      if (!validateBlobGasUsed(header, maybeVersionedHashes.get(), protocolSpec)) {
        return ValidationResult.invalid(
            RpcErrorType.INVALID_PARAMS,
            "Payload BlobGasUsed does not match calculated BlobGasUsed");
      }
    }

    if (protocolSpec.getGasCalculator().blobGasCost(transactionVersionedHashes.size())
        > protocolSpec.getGasLimitCalculator().currentBlobGasLimit()) {
      return ValidationResult.invalid(
          RpcErrorType.INVALID_PARAMS,
          String.format("Invalid Blob Count: %d", transactionVersionedHashes.size()));
    }
    return ValidationResult.valid();
  }

  private boolean validateExcessBlobGas(
      final BlockHeader header, final BlockHeader parentHeader, final ProtocolSpec protocolSpec) {
    BlobGas calculatedBlobGas =
        ExcessBlobGasCalculator.calculateExcessBlobGasForParent(protocolSpec, parentHeader);
    return header.getExcessBlobGas().orElse(BlobGas.ZERO).equals(calculatedBlobGas);
  }

  private boolean validateBlobGasUsed(
      final BlockHeader header,
      final List<VersionedHash> maybeVersionedHashes,
      final ProtocolSpec protocolSpec) {
    var calculatedBlobGas =
        protocolSpec.getGasCalculator().blobGasCost(maybeVersionedHashes.size());
    return header.getBlobGasUsed().orElse(0L).equals(calculatedBlobGas);
  }

  private Optional<List<VersionedHash>> extractVersionedHashes(
      final Optional<List<String>> maybeVersionedHashParam) {
    return maybeVersionedHashParam.map(
        versionedHashes ->
            versionedHashes.stream()
                .map(Bytes32::fromHexString)
                .map(
                    hash -> {
                      try {
                        return new VersionedHash(hash);
                      } catch (InvalidParameterException e) {
                        throw new RuntimeException(e);
                      }
                    })
                .collect(Collectors.toList()));
  }

  private void logImportedBlockInfo(final Block block, final double timeInS) {
    final StringBuilder message = new StringBuilder();
    message.append("Imported #%,d / %d tx");
    final List<Object> messageArgs =
        new ArrayList<>(
            List.of(block.getHeader().getNumber(), block.getBody().getTransactions().size()));
    if (block.getBody().getWithdrawals().isPresent()) {
      message.append(" / %d ws");
      messageArgs.add(block.getBody().getWithdrawals().get().size());
    }
    if (block.getBody().getDeposits().isPresent()) {
      message.append(" / %d ds");
      messageArgs.add(block.getBody().getDeposits().get().size());
    }
    message.append(" / base fee %s / %,d (%01.1f%%) gas / (%s) in %01.3fs. Peers: %d");
    messageArgs.addAll(
        List.of(
            block.getHeader().getBaseFee().map(Wei::toHumanReadableString).orElse("N/A"),
            block.getHeader().getGasUsed(),
            (block.getHeader().getGasUsed() * 100.0) / block.getHeader().getGasLimit(),
            block.getHash().toHexString(),
            timeInS,
            ethPeers.peerCount()));
    LOG.info(String.format(message.toString(), messageArgs.toArray()));
  }
}
