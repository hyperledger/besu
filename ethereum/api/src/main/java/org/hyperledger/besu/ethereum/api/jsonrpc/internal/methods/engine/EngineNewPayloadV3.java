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

import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.INVALID_PARAMS;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError.UNSUPPORTED_FORK;

import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;

public class EngineNewPayloadV3 extends AbstractEngineNewPayload {

  private final ProtocolSchedule timestampSchedule;

  public EngineNewPayloadV3(
      final Vertx vertx,
      final ProtocolSchedule timestampSchedule,
      final ProtocolContext protocolContext,
      final MergeMiningCoordinator mergeCoordinator,
      final EthPeers ethPeers,
      final EngineCallListener engineCallListener) {
    super(
        vertx, timestampSchedule, protocolContext, mergeCoordinator, ethPeers, engineCallListener);
    this.timestampSchedule = timestampSchedule;
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_NEW_PAYLOAD_V3.getMethodName();
  }

  @Override
  protected Optional<JsonRpcResponse> validateForkSupported(
      final Object reqId, final EnginePayloadParameter payloadParameter) {
    var cancun = timestampSchedule.hardforkFor(s -> s.fork().name().equalsIgnoreCase("Cancun"));

    if (cancun.isPresent() && payloadParameter.getTimestamp() >= cancun.get().milestone()) {
      if (payloadParameter.getDataGasUsed() == null
          || payloadParameter.getExcessDataGas() == null) {
        return Optional.of(new JsonRpcErrorResponse(reqId, INVALID_PARAMS));
      }
    } else {
      if (payloadParameter.getDataGasUsed() != null
          || payloadParameter.getExcessDataGas() != null) {
        return Optional.of(new JsonRpcErrorResponse(reqId, UNSUPPORTED_FORK));
      }
    }
    return Optional.empty();
  }

  @Override
  protected Optional<JsonRpcResponse> validateTransactions(
      final EnginePayloadParameter blockParam,
      final Object reqId,
      final List<Transaction> transactions,
      final Optional<List<String>> maybeVersionedHashParam) {

    var blobTransactions =
        transactions.stream().filter(transaction -> transaction.getType().supportsBlob()).toList();

    return validateBlobTransactions(blockParam, reqId, blobTransactions, maybeVersionedHashParam);
  }

  private Optional<JsonRpcResponse> validateBlobTransactions(
      final EnginePayloadParameter blockParam,
      final Object reqId,
      final List<Transaction> blobTransactions,
      final Optional<List<String>> maybeVersionedHashParam) {

    List<Bytes32> versionedHashesParam =
        maybeVersionedHashParam
            .map(strings -> strings.stream().map(Bytes32::fromHexStringStrict).toList())
            .orElseGet(ArrayList::new);

    final List<Bytes32> transactionVersionedHashes = new ArrayList<>();

    for (Transaction transaction : blobTransactions) {
      if (transaction.getType().supportsBlob()) {
        var versionedHashes = transaction.getVersionedHashes();
        if (versionedHashes.isEmpty()) {
          return Optional.of(
              respondWithInvalid(
                  reqId, blockParam, null, INVALID, "There must be at least one blob"));
        }
        transactionVersionedHashes.addAll(
            versionedHashes.get().stream().map(VersionedHash::toBytes).toList());
      }
    }

    // check list contents
    if (!versionedHashesParam.equals(transactionVersionedHashes)) {
      return Optional.of(
          respondWithInvalid(
              reqId,
              blockParam,
              null,
              INVALID,
              "Versioned hashes from blob transactions do not match expected values"));
    }
    return Optional.empty();
  }
}
