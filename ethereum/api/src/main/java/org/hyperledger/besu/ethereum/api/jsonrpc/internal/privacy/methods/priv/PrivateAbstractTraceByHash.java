/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.PrivacyIdProvider;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor.PrivateBlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor.PrivateBlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor.PrivateTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor.PrivateTransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.privateTracing.PrivateFlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.privacy.privateTracing.PrivateTraceGenerator;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.PrivacyQueries;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.ExecutedPrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.MultiTenancyPrivacyController;
import org.hyperledger.besu.ethereum.privacy.PrivacyController;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public abstract class PrivateAbstractTraceByHash implements JsonRpcMethod {

  protected final Supplier<PrivateBlockTracer> blockTracerSupplier;
  protected final BlockchainQueries blockchainQueries;
  protected final PrivacyQueries privacyQueries;
  protected final ProtocolSchedule protocolSchedule;
  protected final PrivacyController privacyController;
  protected final PrivacyParameters privacyParameters;
  protected final PrivacyIdProvider privacyIdProvider;

  protected PrivateAbstractTraceByHash(
      final Supplier<PrivateBlockTracer> blockTracerSupplier,
      final BlockchainQueries blockchainQueries,
      final PrivacyQueries privacyQueries,
      final ProtocolSchedule protocolSchedule,
      final PrivacyController privacyController,
      final PrivacyParameters privacyParameters,
      final PrivacyIdProvider privacyIdProvider) {
    this.blockTracerSupplier = blockTracerSupplier;
    this.blockchainQueries = blockchainQueries;
    this.privacyQueries = privacyQueries;
    this.protocolSchedule = protocolSchedule;
    this.privacyController = privacyController;
    this.privacyParameters = privacyParameters;
    this.privacyIdProvider = privacyIdProvider;
  }

  public Stream<PrivateFlatTrace> resultByTransactionHash(
      final Hash transactionHash, final JsonRpcRequestContext requestContext) {

    final String enclaveKey = privacyIdProvider.getPrivacyUserId(requestContext.getUser());
    final String privacyGroupId = requestContext.getRequiredParameter(0, String.class);
    if (privacyController instanceof MultiTenancyPrivacyController) {
      checkIfPrivacyGroupMatchesAuthenticatedEnclaveKey(
          requestContext, privacyGroupId, Optional.empty());
    }

    return privacyController
        .findPrivateTransactionByPmtHash(transactionHash, enclaveKey)
        .map(ExecutedPrivateTransaction::getBlockNumber)
        .flatMap(blockNumber -> blockchainQueries.getBlockchain().getBlockHashByNumber(blockNumber))
        .map(blockHash -> getTraceBlock(blockHash, transactionHash, enclaveKey, privacyGroupId))
        .orElse(Stream.empty());
  }

  private Stream<PrivateFlatTrace> getTraceBlock(
      final Hash blockHash,
      final Hash transactionHash,
      final String enclaveKey,
      final String privacyGroupId) {
    Block block = blockchainQueries.getBlockchain().getBlockByHash(blockHash).orElse(null);
    PrivateBlockMetadata privateBlockMetadata =
        privacyQueries.getPrivateBlockMetaData(privacyGroupId, blockHash).orElse(null);

    if (privateBlockMetadata == null || block == null) {
      return Stream.empty();
    }
    return PrivateTracer.processTracing(
            blockchainQueries,
            Optional.of(block.getHeader()),
            privacyGroupId,
            enclaveKey,
            privacyParameters,
            privacyController,
            mutableWorldState -> {
              final PrivateTransactionTrace privateTransactionTrace =
                  getTransactionTrace(
                      block, transactionHash, enclaveKey, privateBlockMetadata, privacyGroupId);
              return Optional.ofNullable(getTraceStream(privateTransactionTrace, block));
            })
        .orElse(Stream.empty());
  }

  private PrivateTransactionTrace getTransactionTrace(
      final Block block,
      final Hash transactionHash,
      final String enclaveKey,
      final PrivateBlockMetadata privateBlockMetadata,
      final String privacyGroupId) {
    return PrivateTracer.processTracing(
            blockchainQueries,
            Optional.of(block.getHeader()),
            privacyGroupId,
            enclaveKey,
            privacyParameters,
            privacyController,
            mutableWorldState ->
                blockTracerSupplier
                    .get()
                    .trace(
                        mutableWorldState,
                        block,
                        new DebugOperationTracer(new TraceOptions(false, false, true)),
                        enclaveKey,
                        privacyGroupId,
                        privateBlockMetadata)
                    .map(PrivateBlockTrace::getTransactionTraces)
                    .orElse(Collections.emptyList())
                    .stream()
                    .filter(
                        trxTrace ->
                            trxTrace.getPrivateTransaction().getPmtHash().equals(transactionHash))
                    .findFirst())
        .orElseThrow();
  }

  private Stream<PrivateFlatTrace> getTraceStream(
      final PrivateTransactionTrace transactionTrace, final Block block) {

    return PrivateTraceGenerator.generateFromTransactionTraceAndBlock(
            this.protocolSchedule, transactionTrace, block)
        .map(PrivateFlatTrace.class::cast);
  }

  protected JsonNode arrayNodeFromTraceStream(final Stream<PrivateFlatTrace> traceStream) {
    final ObjectMapper mapper = new ObjectMapper();
    final ArrayNode resultArrayNode = mapper.createArrayNode();
    traceStream.forEachOrdered(resultArrayNode::addPOJO);
    return resultArrayNode;
  }

  private void checkIfPrivacyGroupMatchesAuthenticatedEnclaveKey(
      final JsonRpcRequestContext request,
      final String privacyGroupId,
      final Optional<Long> toBlock) {
    final String privacyUserId = privacyIdProvider.getPrivacyUserId(request.getUser());
    privacyController.verifyPrivacyGroupContainsPrivacyUserId(
        privacyGroupId, privacyUserId, toBlock);
  }
}
