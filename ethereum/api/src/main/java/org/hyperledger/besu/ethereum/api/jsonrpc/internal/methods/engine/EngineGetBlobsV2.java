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

import org.hyperledger.besu.datatypes.BlobProofBundle;
import org.hyperledger.besu.datatypes.KZGProof;
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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlobsBundleV2;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import ethereum.ckzg4844.CKZG4844JNI;
import ethereum.ckzg4844.CellsAndProofs;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineGetBlobsV2 extends ExecutionEngineJsonRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(EngineGetBlobsV2.class);

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

    final List<BlobAndProofV2> result = getBlobV2Result(versionedHashes);

    return new JsonRpcSuccessResponse(requestContext.getRequest().getId(), result);
  }

  private @Nonnull List<BlobAndProofV2> getBlobV2Result(final VersionedHash[] versionedHashes) {
    return Arrays.stream(versionedHashes)
        .map(transactionPool::getBlobProofBundle)
        .map(this::getBlobAndProofV2)
        .toList();
  }

  private @Nullable BlobAndProofV2 getBlobAndProofV2(final BlobProofBundle bq) {
    if (bq == null) {
      return null;
    }
    BlobProofBundle toReturn = bq;
    if (bq.versionId() == BlobProofBundle.VERSION_0_KZG_PROOFS) {
      LOG.info(
          "BlobProofBundle {} versionId is 0. Converting to version {}",
          bq.versionedHash(),
          BlobProofBundle.VERSION_1_KZG_CELL_PROOFS);
      CellsAndProofs cellProofs =
          CKZG4844JNI.computeCellsAndKzgProofs(bq.blob().getData().toArray());
      List<KZGProof> kzgCellProofs = extractKZGProofs(cellProofs.getProofs());
      toReturn =
          BlobProofBundle.builder()
              .versionId(BlobProofBundle.VERSION_1_KZG_CELL_PROOFS)
              .blob(bq.blob())
              .kzgCommitment(bq.kzgCommitment())
              .kzgProof(kzgCellProofs)
              .versionedHash(bq.versionedHash())
              .build();
    }
    return new BlobAndProofV2(
        toReturn.blob().getData().toHexString(),
        toReturn.kzgProof().stream().map(p -> p.getData().toHexString()).toList());
  }

  public static List<KZGProof> extractKZGProofs(final byte[] input) {
    return BlobsBundleV2.extractKZGProofs(input);
  }
}
