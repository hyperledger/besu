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
package org.hyperledger.besu.ethereum.core.kzg;

import static org.hyperledger.besu.ethereum.core.kzg.BlobProofBundle.VERSION_0_KZG_PROOFS;
import static org.hyperledger.besu.ethereum.core.kzg.BlobProofBundle.VERSION_1_KZG_CELL_PROOFS;

import java.util.ArrayList;
import java.util.List;

import ethereum.ckzg4844.CKZG4844JNI;
import ethereum.ckzg4844.CellsAndProofs;
import org.apache.tuweni.bytes.Bytes48;

public class KzgHelper {

  /**
   * Converts the given BlobsWithCommitments to version 1.
   *
   * @param blobsWithCommitments the BlobsWithCommitments to convert.
   * @return a new BlobsWithCommitments instance with version 1 and updated proofs.
   * @throws IllegalArgumentException if the blobs with commitments are not valid for conversion.
   */
  public static BlobsWithCommitments convertToVersion1(
      final BlobsWithCommitments blobsWithCommitments) {
    if (blobsWithCommitments.getVersionId() == VERSION_1_KZG_CELL_PROOFS) {
      return blobsWithCommitments;
    }
    // Check if the blobs with commitments are valid for conversion
    boolean isValidBlobsWithCommitments = verify4844Kzg(blobsWithCommitments);
    if (!isValidBlobsWithCommitments) {
      throw new IllegalArgumentException(
          "Invalid blobs with commitments for conversion to version 1");
    }

    List<BlobProofBundle> version1Bundles = new ArrayList<>();
    for (BlobProofBundle bundle : blobsWithCommitments.getBlobProofBundles()) {
      version1Bundles.add(unsafeConvertToVersion1(bundle));
    }
    return new BlobsWithCommitments(version1Bundles);
  }

  private static List<KZGProof> extractKZGProofs(final byte[] input) {
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

  public static BlobProofBundle unsafeConvertToVersion1(final BlobProofBundle bundle) {
    if (bundle.getVersionId() == VERSION_1_KZG_CELL_PROOFS) {
      return bundle;
    }
    CellsAndProofs cellProofs =
        CKZG4844JNI.computeCellsAndKzgProofs(bundle.getBlob().getData().toArray());
    List<KZGProof> kzgCellProofs = extractKZGProofs(cellProofs.getProofs());
    return new BlobProofBundle(
        VERSION_1_KZG_CELL_PROOFS,
        bundle.getBlob(),
        bundle.getKzgCommitment(),
        kzgCellProofs,
        bundle.getVersionedHash());
  }

  public static boolean verify4844Kzg(final BlobsWithCommitments blobsWithCommitments) {
    return switch (blobsWithCommitments.getVersionId()) {
      case VERSION_0_KZG_PROOFS ->
          CKZG4844JNI.verifyBlobKzgProofBatch(
              blobsWithCommitments.getBlobsByteArray(),
              blobsWithCommitments.getKzgCommitmentsByteArray(),
              blobsWithCommitments.getKzgProofsByteArray(),
              blobsWithCommitments.getBlobProofBundles().size());
      case VERSION_1_KZG_CELL_PROOFS ->
          CKZG4844JNI.verifyCellKzgProofBatch(
              blobsWithCommitments.getKzgCommitmentsByteArray(),
              blobsWithCommitments.getCellIndexes(),
              blobsWithCommitments.getBlobCellsByteArray(),
              blobsWithCommitments.getKzgProofsByteArray());
      default ->
          throw new IllegalArgumentException(
              "Unsupported blob version: " + blobsWithCommitments.getVersionId());
    };
  }
}
