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
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;

/** A class to hold the blobs, commitments, proofs, and versioned hashes for a set of blobs. */
public class BlobsWithCommitments {

  private final List<BlobProofBundle> blobProofBundles;
  private final int versionId;

  /**
   * Constructs a {@link BlobsWithCommitments} instance.
   *
   * @param versionId version ID for the sidecar.
   * @param kzgCommitments commitments for the blobs.
   * @param blobs list of blobs to be committed to.
   * @param kzgProofs proofs for the commitments.
   * @param kzgCellProofs cell proofs for the commitments.
   * @param versionedHashes hashes of the commitments.
   * @throws InvalidParameterException if the input parameters are invalid.
   */
  public BlobsWithCommitments(
      final int versionId,
      final List<KZGCommitment> kzgCommitments,
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs,
      final List<KZGCellProof> kzgCellProofs,
      final List<VersionedHash> versionedHashes) {
    if (blobs.isEmpty()) {
      throw new InvalidParameterException(
          "There needs to be a minimum of one blob in a blob transaction with commitments");
    }
    List<BlobProofBundle> toBuild = new ArrayList<>(blobs.size());
    for (int i = 0; i < blobs.size(); i++) {
      validateBlobWithCommitments(
          versionId, kzgCommitments, blobs, kzgProofs, kzgCellProofs, versionedHashes);
      BlobProofBundle.Builder builder =
          BlobProofBundle.builder()
              .versionId(versionId)
              .blob(blobs.get(i))
              .kzgCommitment(kzgCommitments.get(i))
              .versionedHash(versionedHashes.get(i));
      switch (versionId) {
        case BlobProofBundle.VERSION_0_KZG_PROOFS:
          builder.kzgProof(kzgProofs.get(i));
          break;
        case BlobProofBundle.VERSION_1_KZG_CELL_PROOFS:
          builder.kzgCellProof(extractCellProofs(kzgCellProofs, i));
          break;
        default:
          throw new InvalidParameterException("Invalid kzg version");
      }
      toBuild.add(builder.build());
    }
    this.blobProofBundles = toBuild;
    this.versionId = versionId;
  }

  /**
   * Extracts cell proofs for a specific blob index.
   *
   * @param proofList the list of cell proofs.
   * @param index the index of the blob.
   * @return the list of cell proofs for the specified blob.
   */
  private static List<KZGCellProof> extractCellProofs(
      final List<KZGCellProof> proofList, final int index) {
    return proofList.subList(
        index * BlobProofBundle.CELL_PROOFS_PER_BLOB,
        (index + 1) * BlobProofBundle.CELL_PROOFS_PER_BLOB);
  }

  /**
   * Validates the input parameters for constructing a {@link BlobsWithCommitments}.
   *
   * @param versionId the version ID.
   * @param kzgCommitments the list of KZG commitments.
   * @param blobs the list of blobs.
   * @param kzgProofs the list of KZG proofs.
   * @param kzgCellProofs the list of KZG cell proofs.
   * @param versionedHashes the list of versioned hashes.
   * @throws InvalidParameterException if the input parameters are invalid.
   */
  @VisibleForTesting
  public static void validateBlobWithCommitments(
      final int versionId,
      final List<KZGCommitment> kzgCommitments,
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs,
      final List<KZGCellProof> kzgCellProofs,
      final List<VersionedHash> versionedHashes) {
    if (blobs.size() != kzgCommitments.size()) {
      String error =
          String.format(
              "Invalid number of kzgCommitments, expected %s, got %s",
              blobs.size(), kzgCommitments.size());
      throw new InvalidParameterException(error);
    }
    if (blobs.size() != versionedHashes.size()) {
      String error =
          String.format(
              "Invalid number of versionedHashes, expected %s, got %s",
              blobs.size(), versionedHashes.size());
      throw new InvalidParameterException(error);
    }
    switch (versionId) {
      case BlobProofBundle.VERSION_0_KZG_PROOFS:
        validateBlobWithCommitmentsV0(blobs, kzgProofs, kzgCellProofs);
        break;
      case BlobProofBundle.VERSION_1_KZG_CELL_PROOFS:
        validateBlobWithCommitmentsV1(blobs, kzgProofs, kzgCellProofs);
        break;
      default:
        throw new InvalidParameterException("Invalid kzg version");
    }
  }

  /**
   * Validates the input parameters for version 0 KZG proofs.
   *
   * @param blobs the list of blobs.
   * @param kzgProofs the list of KZG proofs.
   * @param kzgCellProofs the list of KZG cell proofs.
   * @throws InvalidParameterException if the input parameters are invalid.
   */
  private static void validateBlobWithCommitmentsV0(
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs,
      final List<KZGCellProof> kzgCellProofs) {
    if (blobs.size() != kzgProofs.size()) {
      String error =
          String.format(
              "Invalid number of kzgProofs, expected %s, got %s", blobs.size(), kzgProofs.size());
      throw new InvalidParameterException(error);
    }
    if (!kzgCellProofs.isEmpty()) {
      throw new InvalidParameterException("Version 0 does not support cell proofs");
    }
  }

  /**
   * Validates the input parameters for version 1 KZG cell proofs.
   *
   * @param blobs the list of blobs.
   * @param kzgProofs the list of KZG proofs.
   * @param kzgCellProofs the list of KZG cell proofs.
   * @throws InvalidParameterException if the input parameters are invalid.
   */
  private static void validateBlobWithCommitmentsV1(
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs,
      final List<KZGCellProof> kzgCellProofs) {
    if (!kzgProofs.isEmpty()) {
      throw new InvalidParameterException("Version 1 does not support kzgProofs");
    }
    int expectedCellProofsTotal = BlobProofBundle.CELL_PROOFS_PER_BLOB * blobs.size();
    if (kzgCellProofs.size() != expectedCellProofsTotal) {
      String error =
          String.format(
              "Invalid number of cell proofs, expected %s, got %s",
              expectedCellProofsTotal, kzgCellProofs.size());
      throw new InvalidParameterException(error);
    }
  }

  /**
   * Constructs a {@link BlobsWithCommitments} instance from a list of {@link BlobProofBundle}.
   *
   * @param blobProofBundles the list of blob proof bundles to be attached to the transaction.
   */
  public BlobsWithCommitments(final List<BlobProofBundle> blobProofBundles) {
    this.blobProofBundles = blobProofBundles;
    this.versionId = blobProofBundles.get(0).versionId();
  }

  /**
   * Get the blobs.
   *
   * @return the blobs.
   */
  public List<Blob> getBlobs() {
    return blobProofBundles.stream().map(BlobProofBundle::blob).toList();
  }

  /**
   * Get the commitments.
   *
   * @return the commitments.
   */
  public List<KZGCommitment> getKzgCommitments() {
    return blobProofBundles.stream().map(BlobProofBundle::kzgCommitment).toList();
  }

  /**
   * Get the proofs.
   *
   * @return the proofs.
   */
  public List<KZGProof> getKzgProofs() {
    return blobProofBundles.stream()
        .filter(Objects::nonNull)
        .map(BlobProofBundle::kzgProof)
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * Get the cell proofs.
   *
   * @return the cell proofs.
   */
  public List<KZGCellProof> getKzgCellProofs() {
    return blobProofBundles.stream()
        .filter(Objects::nonNull)
        .flatMap(
            blobProofBundle -> {
              List<KZGCellProof> proofStream = blobProofBundle.kzgCellProof();
              return proofStream != null ? proofStream.stream() : Stream.empty();
            })
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * Get the hashes.
   *
   * @return the hashes.
   */
  public List<VersionedHash> getVersionedHashes() {
    return blobProofBundles.stream().map(BlobProofBundle::versionedHash).toList();
  }

  /**
   * Get the list of {@link BlobProofBundle}.
   *
   * @return the blob proof bundles.
   */
  public List<BlobProofBundle> getBlobProofBundles() {
    return blobProofBundles;
  }

  /**
   * Get the version ID.
   *
   * @return the version ID.
   */
  public int getVersionId() {
    return versionId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlobsWithCommitments that = (BlobsWithCommitments) o;
    return Objects.equals(getBlobProofBundles(), that.getBlobProofBundles());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBlobProofBundles());
  }
}
