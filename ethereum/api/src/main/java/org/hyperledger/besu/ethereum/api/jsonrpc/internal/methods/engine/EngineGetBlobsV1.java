/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.datatypes.BlobsWithCommitments;
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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlobAndProofV1;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.vertx.core.Vertx;

/**
 * #### Specification
 *
 * <p>1. Given an array of blob versioned hashes client software **MUST** respond with an array of
 * `BlobAndProofV1` objects with matching versioned hashes, respecting the order of versioned hashes
 * in the input array.
 *
 * <p>2. Client software **MUST** place responses in the order given in the request, using `null`
 * for any missing blobs. For instance, if the request is `[A_versioned_hash, B_versioned_hash,
 * C_versioned_hash]` and client software has data for blobs `A` and `C`, but doesn't have data for
 * `B`, the response **MUST** be `[A, null, C]`.
 *
 * <p>3. Client software **MUST** support request sizes of at least 128 blob versioned hashes. The
 * client **MUST** return `-38004: Too large request` error if the number of requested blobs is too
 * large.
 *
 * <p>4. Client software **MAY** return an array of all `null` entries if syncing or otherwise
 * unable to serve blob pool data.
 *
 * <p>5. Callers **MUST** consider that execution layer clients may prune old blobs from their pool,
 * and will respond with `null` if a blob has been pruned.
 */
public class EngineGetBlobsV1 extends ExecutionEngineJsonRpcMethod {

  private final TransactionPool transactionPool;

  public EngineGetBlobsV1(
      final Vertx vertx,
      final ProtocolContext protocolContext,
      final EngineCallListener engineCallListener,
      final TransactionPool transactionPool) {
    super(vertx, protocolContext, engineCallListener);
    this.transactionPool = transactionPool;
  }

  @Override
  public String getName() {
    return "engine_getBlobsV1";
  }

  @Override
  public JsonRpcResponse syncResponse(final JsonRpcRequestContext requestContext) {
    final VersionedHash[] versionedHashes;
    try {
      versionedHashes = requestContext.getRequiredParameter(0, VersionedHash[].class);
    } catch (JsonRpcParameter.JsonRpcParameterException e) {
      throw new InvalidJsonRpcParameters(
          "Invalid versioned hashes parameter (index 0)",
          RpcErrorType.INVALID_VERSIONED_HASHES_PARAMS,
          e);
    }

    if (versionedHashes.length > 128) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(),
          RpcErrorType.INVALID_ENGINE_GET_BLOBS_V1_TOO_LARGE_REQUEST);
    }

    final List<BlobAndProofV1> result = getBlobV1Result(versionedHashes);

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
  }

  private @Nonnull List<BlobAndProofV1> getBlobV1Result(final VersionedHash[] versionedHashes) {
    return Arrays.stream(versionedHashes)
        .map(transactionPool::getBlobQuad)
        .map(this::getBlobAndProofV1)
        .toList();
  }

  private @Nullable BlobAndProofV1 getBlobAndProofV1(final BlobsWithCommitments.BlobQuad bq) {
    if (bq == null) {
      return null;
    }
    return new BlobAndProofV1(
        bq.blob().getData().toHexString(), bq.kzgProof().getData().toHexString());
  }
}
