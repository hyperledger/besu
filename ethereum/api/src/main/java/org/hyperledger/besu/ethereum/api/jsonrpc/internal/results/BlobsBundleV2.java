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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.datatypes.BlobType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.kzg.Blob;
import org.hyperledger.besu.ethereum.core.kzg.BlobsWithCommitments;
import org.hyperledger.besu.ethereum.core.kzg.KZGCommitment;
import org.hyperledger.besu.ethereum.core.kzg.KZGProof;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import ethereum.ckzg4844.CKZG4844JNI;
import ethereum.ckzg4844.CellsAndProofs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonPropertyOrder({"commitments", "proofs", "blobs"})
public class BlobsBundleV2 {

  private static final Logger LOG = LoggerFactory.getLogger(BlobsBundleV2.class);
  private final List<String> commitments;

  private final List<String> cellProfs;

  private final List<String> blobs;

  public BlobsBundleV2(final List<Transaction> transactions) {
    final List<BlobsWithCommitments> blobsWithCommitments =
        transactions.stream()
            .map(Transaction::getBlobsWithCommitments)
            .flatMap(Optional::stream)
            .map(this::mapBlobWithCommitments)
            .toList();

    this.commitments =
        blobsWithCommitments.stream()
            .flatMap(b -> b.getKzgCommitments().stream())
            .map(KZGCommitment::getData)
            .map(Bytes::toString)
            .collect(Collectors.toList());

    this.cellProfs =
        blobsWithCommitments.stream()
            .flatMap(b -> b.getKzgProofs().stream())
            .map(KZGProof::getData)
            .map(Bytes::toString)
            .collect(Collectors.toList());

    this.blobs =
        blobsWithCommitments.stream()
            .flatMap(b -> b.getBlobs().stream())
            .map(Blob::getData)
            .map(Bytes::toString)
            .collect(Collectors.toList());

    LOG.info(
        "BlobsBundleV2: totalTxs: {}, blobTxs: {}, commitments: {}, cell proofs: {}, blobs: {}",
        transactions.size(),
        blobsWithCommitments.size(),
        commitments.size(),
        cellProfs.size(),
        blobs.size());
  }

  @JsonGetter("commitments")
  public List<String> getCommitments() {
    return commitments;
  }

  @JsonGetter("proofs")
  public List<String> getCellProofs() {
    return cellProfs;
  }

  @JsonGetter("blobs")
  public List<String> getBlobs() {
    return blobs;
  }

  @SuppressWarnings("UnusedVariable")
  private BlobsWithCommitments mapBlobWithCommitments(
      final BlobsWithCommitments blobsWithCommitments) {
    if (blobsWithCommitments.getBlobType() == BlobType.KZG_CELL_PROOFS) {
      return blobsWithCommitments;
    }
    LOG.info(
        "BlobProofBundle {} versionId is 0. Converting to version {}",
        blobsWithCommitments.getVersionedHashes(),
        BlobType.KZG_CELL_PROOFS);
    List<KZGProof> kzgCellProofs = new ArrayList<>();
    for (Blob blob : blobsWithCommitments.getBlobs()) {
      CellsAndProofs cellProofs = CKZG4844JNI.computeCellsAndKzgProofs(blob.getData().toArray());
      kzgCellProofs.addAll(extractKZGProofs(cellProofs.getProofs()));
    }
    return new BlobsWithCommitments(
        BlobType.KZG_CELL_PROOFS,
        blobsWithCommitments.getKzgCommitments(),
        blobsWithCommitments.getBlobs(),
        kzgCellProofs,
        blobsWithCommitments.getVersionedHashes());
  }

  public static List<KZGProof> extractKZGProofs(final byte[] input) {
    List<KZGProof> chunks = new ArrayList<>();
    int chunkSize = Bytes48.SIZE;
    int totalChunks = input.length / chunkSize;
    for (int i = 0; i < totalChunks; i++) {
      byte[] chunk = new byte[chunkSize];
      System.arraycopy(input, i * chunkSize, chunk, 0, chunkSize);
      chunks.add(new KZGProof(Bytes48.wrap(chunk)));
    }
    return chunks;
  }
}
