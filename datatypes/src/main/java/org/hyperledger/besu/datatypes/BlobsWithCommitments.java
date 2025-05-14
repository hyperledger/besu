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
package org.hyperledger.besu.datatypes;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A class to hold the blobs, commitments, proofs and versioned hashes for a set of blobs. */
public class BlobsWithCommitments {

  public static final int KZG_WITH_PROOFS = 0;
  public static final int KZG_WITH_CELL_PROOFS = 1;
  public static final int CELL_PROOFS_PER_BLOB = 128;

  /**
   * A record to hold the blob, commitment, proof and versioned hash for a blob.
   *
   * @param versionId version id for the sidecar
   * @param blob The blob
   * @param kzgCommitment The commitment
   * @param kzgProof The proof
   * @param kzgCellProof The cell proof
   * @param versionedHash The versioned hash
   */
  public record BlobQuad(
      int versionId,
      Blob blob,
      KZGCommitment kzgCommitment,
      KZGProof kzgProof,
      KZGProof kzgCellProof,
      VersionedHash versionedHash) {}

  private final List<BlobQuad> blobQuads;
  private final int versionId;

  public BlobsWithCommitments(
      final List<KZGCommitment> kzgCommitments,
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs,
      final List<VersionedHash> versionedHashes) {
    this(KZG_WITH_PROOFS, kzgCommitments, blobs, kzgProofs, List.of(), versionedHashes);
  }

  /**
   * A class to hold the blobs, commitments, proofs, cell proofs, version IDs, and versioned hashes
   * for a set of blobs.
   *
   * @param versionId version id for the sidecar
   * @param kzgCommitments commitments for the blobs
   * @param blobs list of blobs to be committed to
   * @param kzgProofs proofs for the commitments
   * @param kzgCellProofs cell proofs for the commitments
   * @param versionedHashes hashes of the commitments
   */
  public BlobsWithCommitments(
      final int versionId,
      final List<KZGCommitment> kzgCommitments,
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs,
      final List<KZGProof> kzgCellProofs,
      final List<VersionedHash> versionedHashes) {
    if (blobs.isEmpty()) {
      throw new InvalidParameterException(
          "There needs to be a minimum of one blob in a blob transaction with commitments");
    }
    switch (versionId) {
      case KZG_WITH_PROOFS:
        validateBlobWithCommitmentsV0(
            kzgCommitments, blobs, kzgProofs, kzgCellProofs, versionedHashes);
        break;
      case KZG_WITH_CELL_PROOFS:
        validateBlobWithCommitmentsV1(
            kzgCommitments, blobs, kzgProofs, kzgCellProofs, versionedHashes);
        break;
      default:
        throw new InvalidParameterException("Invalid kzg version");
    }

    List<BlobQuad> toBuild = new ArrayList<>(blobs.size());
    for (int i = 0; i < blobs.size(); i++) {
      toBuild.add(
          new BlobQuad(
              versionId,
              blobs.get(i),
              kzgCommitments.get(i),
              kzgProofs.get(i),
              kzgCellProofs.get(i),
              versionedHashes.get(i)));
    }
    this.blobQuads = toBuild;
    this.versionId = versionId;
  }

  private void validateBlobWithCommitmentsV0(
      final List<KZGCommitment> kzgCommitments,
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs,
      final List<KZGProof> kzgCellProofs,
      final List<VersionedHash> versionedHashes) {
    if (blobs.size() != kzgCommitments.size()
        || blobs.size() != kzgProofs.size()
        || blobs.size() != versionedHashes.size()) {
      throw new InvalidParameterException(
          "There must be an equal number of blobs, commitments, proofs, and versioned hashes");
    }
    if (!kzgCellProofs.isEmpty()) {
      throw new InvalidParameterException("Version 0 does not support cell proofs");
    }
  }

  private void validateBlobWithCommitmentsV1(
      final List<KZGCommitment> kzgCommitments,
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs,
      final List<KZGProof> kzgCellProofs,
      final List<VersionedHash> versionedHashes) {
    if (blobs.size() != kzgCommitments.size() || blobs.size() != versionedHashes.size()) {
      throw new InvalidParameterException(
          "There must be an equal number of blobs, commitments and versioned hashes");
    }
    if (!kzgProofs.isEmpty()) {
      throw new InvalidParameterException("Version 1 does not support proofs");
    }
    int expectedCellProofsTotal = CELL_PROOFS_PER_BLOB * blobs.size();
    if (kzgCellProofs.size() != expectedCellProofsTotal) {
      String error =
          String.format(
              "Invalid number of cell proofs, expected %s, got %s",
              expectedCellProofsTotal, kzgCellProofs.size());
      throw new InvalidParameterException(error);
    }
  }

  /**
   * Construct the class from a list of BlobQuads.
   *
   * @param quads the list of blob quads to be attached to the transaction
   */
  public BlobsWithCommitments(final List<BlobQuad> quads) {
    this.blobQuads = quads;
    this.versionId = quads.getFirst().versionId;
  }

  /**
   * Get the blobs.
   *
   * @return the blobs
   */
  public List<Blob> getBlobs() {
    return blobQuads.stream().map(BlobQuad::blob).toList();
  }

  /**
   * Get the commitments.
   *
   * @return the commitments
   */
  public List<KZGCommitment> getKzgCommitments() {
    return blobQuads.stream().map(BlobQuad::kzgCommitment).toList();
  }

  /**
   * Get the proofs.
   *
   * @return the proofs
   */
  public List<KZGProof> getKzgProofs() {
    return blobQuads.stream().map(BlobQuad::kzgProof).toList();
  }

  /**
   * Get the cell proofs.
   *
   * @return the cell proofs
   */
  public List<KZGProof> getKzgCellProofs() {
    return blobQuads.stream().map(BlobQuad::kzgCellProof).toList();
  }

  /**
   * Get the hashes.
   *
   * @return the hashes
   */
  public List<VersionedHash> getVersionedHashes() {
    return blobQuads.stream().map(BlobQuad::versionedHash).toList();
  }

  /**
   * Get the list of BlobQuads.
   *
   * @return blob quads
   */
  public List<BlobQuad> getBlobQuads() {
    return blobQuads;
  }

  public int getVersionId() {
    return versionId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlobsWithCommitments that = (BlobsWithCommitments) o;
    return Objects.equals(getBlobQuads(), that.getBlobQuads());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBlobQuads());
  }
}
