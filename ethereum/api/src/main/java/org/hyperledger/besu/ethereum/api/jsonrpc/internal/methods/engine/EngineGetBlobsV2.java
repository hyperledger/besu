/*
 * Copyright contributors to Besu.
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

import org.hyperledger.besu.datatypes.BlobType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlobAndProofV2;
import org.hyperledger.besu.ethereum.core.kzg.BlobProofBundle;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.List;
import java.util.stream.Stream;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineGetBlobsV2 extends ExecutionEngineJsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(EngineGetBlobsV2.class);
  public static final int REQUEST_MAX_VERSIONED_HASHES = 128;

  private final TransactionPool transactionPool;
  private final Counter requestedCounter;
  private final Counter availableCounter;
  private final Counter hitCounter;
  private final Counter missCounter;

  public EngineGetBlobsV2(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final EngineCallListener engineCallListener,
      final TransactionPool transactionPool,
      final MetricsSystem metricsSystem) {
    super(vertx, protocolContext, engineCallListener);
    this.transactionPool = transactionPool;
    // create counters
    this.requestedCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.RPC,
            "execution_engine_getblobs_requested_total",
            "Number of blobs requested via engine_getBlobsV2");
    this.availableCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.RPC,
            "execution_engine_getblobs_available_total",
            "Number of blobs requested via engine_getBlobsV2 that are present in the blob pool");
    this.hitCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.RPC,
            "execution_engine_getblobs_hit_total",
            "Number of calls to engine_getBlobsV2 that returned at least one blob");
    this.missCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.RPC,
            "execution_engine_getblobs_miss_total",
            "Number of calls to engine_getBlobsV2 that returned zero blobs");
  }

  @Override
  public String getName() {
    return RpcMethod.ENGINE_GET_BLOBS_V2.getMethodName();
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final VersionedHash[] versionedHashes = extractVersionedHashes(requestContext);
    // metrics: requested blobs
    if (versionedHashes.length > REQUEST_MAX_VERSIONED_HASHES) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          RpcErrorType.INVALID_ENGINE_GET_BLOBS_TOO_LARGE_REQUEST);
    }
    requestedCounter.inc(versionedHashes.length);
    final List<BlobAndProofV2> result = Stream.of(versionedHashes).map(this::getBlobAndProofOrNull).toList();
    long available = result.stream().filter(java.util.Objects::nonNull).count();
    availableCounter.inc(available);
    if (available > 0) {
      hitCounter.inc();
    } else {
      missCounter.inc();
    }
    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
  }

  private VersionedHash[] extractVersionedHashes(final JsonRpcRequestContext requestContext) {
    try {
      return requestContext.getRequiredParameter(0, VersionedHash[].class);
    } catch (JsonRpcParameter.JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid versioned hashes parameter (index 0)",
          RpcErrorType.INVALID_VERSIONED_HASHES_PARAMS,
          e);
    }
  }

  private BlobAndProofV2 getBlobAndProofOrNull(final VersionedHash versionedHash) {
    final BlobProofBundle bundle = transactionPool.getBlobProofBundle(versionedHash);
    if (bundle == null) {
      LOG.trace("No BlobProofBundle found for versioned hash: {}", versionedHash);
      return null;
    }
    if (bundle.getBlobType() == BlobType.KZG_PROOF) {
      LOG.trace("Unsupported blob type KZG_PROOF for versioned hash: {}", versionedHash);
      return null;
    }
    return createBlobAndProofV2(bundle);
  }

  private BlobAndProofV2 createBlobAndProofV2(final BlobProofBundle blobProofBundle) {
    return new BlobAndProofV2(
        blobProofBundle.getBlob().getData().toHexString(),
        blobProofBundle.getKzgProof().stream()
            .map(proof -> proof.getData().toHexString())
            .toList());
  }
}
