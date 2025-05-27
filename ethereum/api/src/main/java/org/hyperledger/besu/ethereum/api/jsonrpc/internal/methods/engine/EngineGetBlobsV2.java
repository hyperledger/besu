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

import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.ethereum.ProtocolContext;
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
import org.hyperledger.besu.ethereum.core.kzg.KzgHelper;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineGetBlobsV2 extends ExecutionEngineJsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(EngineGetBlobsV2.class);
  public static final int REQUEST_MAX_VERSIONED_HASHES = 128;

  private final TransactionPool transactionPool;

  public EngineGetBlobsV2(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final EngineCallListener engineCallListener,
      final TransactionPool transactionPool) {
    super(vertx, protocolContext, engineCallListener);
    this.transactionPool = transactionPool;
  }

  @Override
  public String getName() {
    return "engine_getBlobsV2";
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final VersionedHash[] versionedHashes = extractVersionedHashes(requestContext);
    if (versionedHashes.length > REQUEST_MAX_VERSIONED_HASHES) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          RpcErrorType.INVALID_ENGINE_GET_BLOBS_V1_TOO_LARGE_REQUEST);
    }
    final List<BlobAndProofV2> result = getBlobV2Result(versionedHashes);
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

  private @Nonnull List<BlobAndProofV2> getBlobV2Result(final VersionedHash[] versionedHashes) {
    return Arrays.stream(versionedHashes)
        .map(transactionPool::getBlobProofBundle)
        .map(this::getBlobAndProofV2)
        .toList();
  }

  private @Nullable BlobAndProofV2 getBlobAndProofV2(final BlobProofBundle blobProofBundle) {
    if (blobProofBundle == null) {
      return null;
    }
    BlobProofBundle proofBundle = processBundle(blobProofBundle);
    return createBlobAndProofV2(proofBundle);
  }

  private BlobProofBundle processBundle(final BlobProofBundle blobProofBundle) {
    if (blobProofBundle.getVersionId() == BlobProofBundle.VERSION_0_KZG_PROOFS) {
      LOG.info(
          "BlobProofBundle {} has versionId 0. Converting to version {}",
          blobProofBundle.getVersionedHash(),
          BlobProofBundle.VERSION_1_KZG_CELL_PROOFS);
      return KzgHelper.unsafeConvertToVersion1(blobProofBundle);
    }
    return blobProofBundle;
  }

  private BlobAndProofV2 createBlobAndProofV2(final BlobProofBundle blobProofBundle) {
    return new BlobAndProofV2(
        blobProofBundle.getBlob().getData().toHexString(),
        blobProofBundle.getKzgProof().stream()
            .map(proof -> proof.getData().toHexString())
            .toList());
  }
}
