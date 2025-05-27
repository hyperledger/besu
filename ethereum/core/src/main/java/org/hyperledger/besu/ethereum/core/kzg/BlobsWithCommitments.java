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

import static org.hyperledger.besu.ethereum.core.kzg.BlobProofBundle.CELL_PROOFS_PER_BLOB;
import static org.hyperledger.besu.ethereum.core.kzg.BlobProofBundle.VERSION_0_KZG_PROOFS;
import static org.hyperledger.besu.ethereum.core.kzg.BlobProofBundle.VERSION_1_KZG_CELL_PROOFS;

import org.hyperledger.besu.datatypes.VersionedHash;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.common.annotations.VisibleForTesting;
import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;

/** A class to hold the blobs, commitments, proofs, and versioned hashes for a set of blobs. */
public class BlobsWithCommitments implements org.hyperledger.besu.datatypes.BlobsWithCommitments {

  private final List<BlobProofBundle> blobProofBundles;
  private final int versionId;

  /**
   * Constructs a {@link BlobsWithCommitments} instance.
   *
   * @param versionId version ID for the sidecar.
   * @param kzgCommitments commitments for the blobs.
   * @param blobs list of blobs to be committed to.
   * @param kzgProofs proofs for the commitments.
   * @param versionedHashes hashes of the commitments.
   * @throws InvalidParameterException if the input parameters are invalid.
   */
  public BlobsWithCommitments(
      final int versionId,
      final List<KZGCommitment> kzgCommitments,
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs,
      final List<VersionedHash> versionedHashes) {
    if (blobs.isEmpty()) {
      throw new InvalidParameterException(
          "There needs to be a minimum of one blob in a blob transaction with commitments");
    }
    List<BlobProofBundle> toBuild = new ArrayList<>(blobs.size());
    for (int i = 0; i < blobs.size(); i++) {
      validateBlobWithCommitments(versionId, kzgCommitments, blobs, kzgProofs, versionedHashes);
      BlobProofBundle.Builder builder =
          BlobProofBundle.builder()
              .versionId(versionId)
              .blob(blobs.get(i))
              .kzgCommitment(kzgCommitments.get(i))
              .versionedHash(versionedHashes.get(i));
      switch (versionId) {
        case BlobProofBundle.VERSION_0_KZG_PROOFS:
          builder.kzgProof(List.of(kzgProofs.get(i)));
          break;
        case BlobProofBundle.VERSION_1_KZG_CELL_PROOFS:
          builder.kzgProof(extractCellProofs(kzgProofs, i));
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
  private static List<KZGProof> extractCellProofs(final List<KZGProof> proofList, final int index) {
    return proofList.subList(index * CELL_PROOFS_PER_BLOB, (index + 1) * CELL_PROOFS_PER_BLOB);
  }

  /**
   * Validates the input parameters for constructing a {@link BlobsWithCommitments}.
   *
   * @param versionId the version ID.
   * @param kzgCommitments the list of KZG commitments.
   * @param blobs the list of blobs.
   * @param kzgProofs the list of KZG proofs.
   * @param versionedHashes the list of versioned hashes.
   * @throws InvalidParameterException if the input parameters are invalid.
   */
  @VisibleForTesting
  public static void validateBlobWithCommitments(
      final int versionId,
      final List<KZGCommitment> kzgCommitments,
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs,
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
        if (blobs.size() != kzgProofs.size()) {
          String error =
              String.format(
                  "Invalid number of kzgProofs, expected %s, got %s",
                  blobs.size(), kzgProofs.size());
          throw new InvalidParameterException(error);
        }
        break;
      case BlobProofBundle.VERSION_1_KZG_CELL_PROOFS:
        int expectedCellProofsTotal = CELL_PROOFS_PER_BLOB * blobs.size();
        if (kzgProofs.size() != expectedCellProofsTotal) {
          String error =
              String.format(
                  "Invalid number of cell proofs, expected %s, got %s",
                  expectedCellProofsTotal, kzgProofs.size());
          throw new InvalidParameterException(error);
        }
        break;
      default:
        throw new InvalidParameterException("Invalid kzg version");
    }
  }

  /**
   * Constructs a {@link BlobsWithCommitments} instance from a list of {@link BlobProofBundle}.
   *
   * @param blobProofBundles the list of blob proof bundles to be attached to the transaction.
   */
  public BlobsWithCommitments(final List<BlobProofBundle> blobProofBundles) {
    this.blobProofBundles = blobProofBundles;
    this.versionId = blobProofBundles.getFirst().getVersionId();
  }

  private int getSize() {
    return blobProofBundles.size();
  }

  /**
   * Get the blobs.
   *
   * @return the blobs.
   */
  @Override
  public List<Blob> getBlobs() {
    return blobProofBundles.stream().map(BlobProofBundle::getBlob).toList();
  }

  private byte[] getBlobsByteArray() {
    return Bytes.wrap(getBlobs().stream().map(Blob::getData).toList()).toArrayUnsafe();
  }

  /**
   * Get the commitments.
   *
   * @return the commitments.
   */
  @Override
  public List<KZGCommitment> getKzgCommitments() {
    return blobProofBundles.stream().map(BlobProofBundle::getKzgCommitment).toList();
  }

  private byte[] getKzgCommitmentsByteArray() {
    if (versionId == BlobProofBundle.VERSION_1_KZG_CELL_PROOFS) {
      return extendCommitments(getKzgCommitments(), CELL_PROOFS_PER_BLOB);
    }
    return extendCommitments(getKzgCommitments(), 1);
  }

  private static byte[] extendCommitments(final List<KZGCommitment> commitments, final int size) {
    int newSize = commitments.size() * size;
    ArrayList<KZGCommitment> extendedCommitments = new ArrayList<>(newSize);
    for (KZGCommitment kzgCommitment : commitments) {
      for (int i = 0; i < CELL_PROOFS_PER_BLOB; i++) {
        extendedCommitments.add(new KZGCommitment(kzgCommitment.getData()));
      }
    }
    return Bytes.wrap(extendedCommitments.stream().map(kc -> (Bytes) kc.getData()).toList())
        .toArrayUnsafe();
  }

  /**
   * Get the proofs.
   *
   * @return the proofs.
   */
  @Override
  public List<KZGProof> getKzgProofs() {
    return blobProofBundles.stream()
        .flatMap(blobProofBundle -> blobProofBundle.getKzgProof().stream())
        .toList();
  }

  private byte[] getKzgProofsByteArray() {
    return Bytes.wrap(getKzgProofs().stream().map(kp -> (Bytes) kp.getData()).toList())
        .toArrayUnsafe();
  }

  /**
   * Get the hashes.
   *
   * @return the hashes.
   */
  @Override
  public List<VersionedHash> getVersionedHashes() {
    return blobProofBundles.stream().map(BlobProofBundle::getVersionedHash).toList();
  }

  /**
   * Get the list of {@link BlobProofBundle}.
   *
   * @return the blob proof bundles.
   */
  public List<BlobProofBundle> getBlobProofBundles() {
    return blobProofBundles;
  }

  private byte[] getBlobCellsByteArray() {
    return Bytes.wrap(blobProofBundles.stream().map(BlobProofBundle::getBlobCellsBytes).toList())
        .toArrayUnsafe();
  }

  private long[] getCellIndexes() {
    long[] cellIndices = new long[CELL_PROOFS_PER_BLOB * blobProofBundles.size()];
    for (int blobIndex = 0; blobIndex < blobProofBundles.size(); blobIndex++) {
      for (int index = 0; index < CELL_PROOFS_PER_BLOB; index++) {
        int cellIndex = blobIndex * CELL_PROOFS_PER_BLOB + index;
        cellIndices[cellIndex] = index;
      }
    }
    return cellIndices;
  }

  /**
   * Get the version ID.
   *
   * @return the version ID.
   */
  @Override
  public int getVersionId() {
    return versionId;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BlobsWithCommitments that = (BlobsWithCommitments) o;
    return versionId == that.versionId
        && Objects.equals(getBlobProofBundles(), that.getBlobProofBundles());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBlobProofBundles(), versionId);
  }

  public static boolean verify4844Kzg(final BlobsWithCommitments blobsWithCommitments) {
    if (blobsWithCommitments.getVersionId() == VERSION_0_KZG_PROOFS) {
      return CKZG4844JNI.verifyBlobKzgProofBatch(
          blobsWithCommitments.getBlobsByteArray(),
          blobsWithCommitments.getKzgCommitmentsByteArray(),
          blobsWithCommitments.getKzgProofsByteArray(),
          blobsWithCommitments.getSize());
    } else if (blobsWithCommitments.getVersionId() == VERSION_1_KZG_CELL_PROOFS) {
      return CKZG4844JNI.verifyCellKzgProofBatch(
          blobsWithCommitments.getKzgCommitmentsByteArray(),
          blobsWithCommitments.getCellIndexes(),
          blobsWithCommitments.getBlobCellsByteArray(),
          blobsWithCommitments.getKzgProofsByteArray());
    } else {
      throw new IllegalArgumentException(
          "Unsupported blob version: " + blobsWithCommitments.getVersionId());
    }
  }
}
