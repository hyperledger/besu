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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.BlobType;
import org.hyperledger.besu.datatypes.VersionedHash;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

/** Represents a bundle of proofs for a blob, including KZG commitments and proofs. */
public final class BlobProofBundle {

  private final BlobType blobType;
  private final Blob blob;
  private final KZGCommitment kzgCommitment;
  private final List<KZGProof> kzgProof;
  private final VersionedHash versionedHash;
  private final Bytes blobCells;

  /**
   * @param blobType the type of the blob
   * @param blob the blob being proven.
   * @param kzgCommitment the KZG commitment for the blob.
   * @param kzgProof the KZG proof for the blob.
   * @param versionedHash the versioned hash of the blob.
   */
  public BlobProofBundle(
      final BlobType blobType,
      final Blob blob,
      final KZGCommitment kzgCommitment,
      final List<KZGProof> kzgProof,
      final VersionedHash versionedHash) {
    checkArgument(kzgCommitment != null, "kzgCommitment must not be empty");
    checkArgument(versionedHash != null, "versionedHash must not be empty");
    checkArgument(blob != null, "blob must not be empty");
    checkArgument(kzgProof != null, "kzgProof must not be empty");
    if (blobType == BlobType.KZG_PROOF && kzgProof.size() != 1) {
      String errorMessage =
          "Invalid kzgProof size for versionId 0, expected 1 but got " + kzgProof.size();
      throw new IllegalArgumentException(errorMessage);
    }
    if (blobType == BlobType.KZG_CELL_PROOFS
        && kzgProof.size() != CKZG4844Helper.CELL_PROOFS_PER_BLOB) {
      String errorMessage =
          "Invalid kzgProof size for versionId 1, expected "
              + CKZG4844Helper.CELL_PROOFS_PER_BLOB
              + " but got "
              + kzgProof.size();
      throw new IllegalArgumentException(errorMessage);
    }
    this.blobType = blobType;
    this.blob = blob;
    this.kzgCommitment = kzgCommitment;
    this.kzgProof = kzgProof;
    this.versionedHash = versionedHash;
    this.blobCells = computeCells(blob, blobType);
  }

  private Bytes computeCells(final Blob blob, final BlobType blobType) {
    if (blobType == BlobType.KZG_CELL_PROOFS) {
      return CKZG4844Helper.computeCells(blob);
    }
    return null;
  }

  public BlobType getBlobType() {
    return blobType;
  }

  public Blob getBlob() {
    return blob;
  }

  public KZGCommitment getKzgCommitment() {
    return kzgCommitment;
  }

  public List<KZGProof> getKzgProof() {
    return kzgProof;
  }

  public VersionedHash getVersionedHash() {
    return versionedHash;
  }

  public Optional<Bytes> getBlobCellsBytes() {
    return Optional.ofNullable(blobCells);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (BlobProofBundle) obj;
    return this.blobType == that.blobType
        && Objects.equals(this.blob, that.blob)
        && Objects.equals(this.kzgCommitment, that.kzgCommitment)
        && Objects.equals(this.kzgProof, that.kzgProof)
        && Objects.equals(this.versionedHash, that.versionedHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(blobType, blob, kzgCommitment, kzgProof, versionedHash);
  }

  @Override
  public String toString() {
    return "BlobProofBundle["
        + "BlobType="
        + blobType
        + ", "
        + "blob="
        + blob
        + ", "
        + "kzgCommitment="
        + kzgCommitment
        + ", "
        + "kzgProof="
        + kzgProof
        + ", "
        + "versionedHash="
        + versionedHash
        + ']';
  }
}
