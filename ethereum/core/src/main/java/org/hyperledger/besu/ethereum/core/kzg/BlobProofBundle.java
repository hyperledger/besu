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

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.datatypes.VersionedHash;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.LongStream;

import ethereum.ckzg4844.CKZG4844JNI;
import ethereum.ckzg4844.CellsAndProofs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;

/** Represents a bundle of proofs for a blob, including KZG commitments and proofs. */
public final class BlobProofBundle {

  /** Version ID for KZG proofs. */
  public static final int VERSION_0_KZG_PROOFS = 0;

  /** Version ID for KZG cell proofs. */
  public static final int VERSION_1_KZG_CELL_PROOFS = 1;

  /** Number of cell proofs per blob. */
  public static final int CELL_PROOFS_PER_BLOB = 128;

  private final int versionId;
  private final Blob blob;
  private final KZGCommitment kzgCommitment;
  private final List<KZGProof> kzgProof;
  private final VersionedHash versionedHash;

  private final Bytes blobCells;

  /**
   * @param versionId the version ID of the bundle.
   * @param blob the blob being proven.
   * @param kzgCommitment the KZG commitment for the blob.
   * @param kzgProof the KZG proof for the blob.
   * @param versionedHash the versioned hash of the blob.
   */
  public BlobProofBundle(
      final int versionId,
      final Blob blob,
      final KZGCommitment kzgCommitment,
      final List<KZGProof> kzgProof,
      final VersionedHash versionedHash) {
    this.versionId = versionId;
    this.blob = blob;
    this.kzgCommitment = kzgCommitment;
    this.kzgProof = kzgProof;
    this.versionedHash = versionedHash;
    this.blobCells = computeCells(blob, versionId);
  }

  private Bytes computeCells(final Blob blob, final int versionId) {
    if (versionId == VERSION_1_KZG_CELL_PROOFS) {
      return Bytes.wrap(CKZG4844JNI.computeCells(blob.getData().toArrayUnsafe()));
    }
    return null;
  }

  /**
   * Converts the current BlobProofBundle to version 1.
   *
   * @return a new BlobProofBundle instance with version 1 and updated proofs.
   */
  public BlobProofBundle convertToVersion1() {
    // Check if the current version is already version 1
    if (versionId == VERSION_1_KZG_CELL_PROOFS) {
      return this; // Return the current instance as it's already version 1
    }
    CellsAndProofs cellProofs = CKZG4844JNI.computeCellsAndKzgProofs(blob.getData().toArray());
    List<KZGProof> kzgCellProofs = extractKZGProofs(cellProofs.getProofs());

    // Use the Builder to create a new BlobProofBundle with version 1
    return BlobProofBundle.builder()
        .versionId(VERSION_1_KZG_CELL_PROOFS)
        .blob(blob)
        .kzgCommitment(kzgCommitment)
        .kzgProof(kzgCellProofs)
        .versionedHash(versionedHash)
        .build();
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

  /**
   * Creates a new {@link Builder} instance for constructing a {@link BlobProofBundle}.
   *
   * @return a new {@link Builder}.
   */
  public static Builder builder() {
    return new Builder();
  }

  public int getVersionId() {
    return versionId;
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

  public Bytes getCommitmentBytes() {
    if (versionId == VERSION_1_KZG_CELL_PROOFS) {
      return extendCommitments(List.of(kzgCommitment));
    }
    return kzgCommitment.getData();
  }

  public long[] getIndices() {
    return LongStream.range(0, CELL_PROOFS_PER_BLOB).toArray();
  }

  public Bytes getProofBytes() {
    return Bytes.wrap(kzgProof.stream().map(kp -> (Bytes) kp.getData()).toList());
  }

  private static Bytes extendCommitments(final List<KZGCommitment> commitments) {
    int newSize = commitments.size() * BlobProofBundle.CELL_PROOFS_PER_BLOB;
    ArrayList<KZGCommitment> extendedCommitments = new ArrayList<>(newSize);
    for (KZGCommitment kzgCommitment : commitments) {
      for (int i = 0; i < BlobProofBundle.CELL_PROOFS_PER_BLOB; i++) {
        extendedCommitments.add(new KZGCommitment(kzgCommitment.getData()));
      }
    }
    return org.apache.tuweni.bytes.Bytes.wrap(
        extendedCommitments.stream().map(kc -> (Bytes) kc.getData()).toList());
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
    return this.versionId == that.versionId
        && Objects.equals(this.blob, that.blob)
        && Objects.equals(this.kzgCommitment, that.kzgCommitment)
        && Objects.equals(this.kzgProof, that.kzgProof)
        && Objects.equals(this.versionedHash, that.versionedHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(versionId, blob, kzgCommitment, kzgProof, versionedHash);
  }

  @Override
  public String toString() {
    return "BlobProofBundle["
        + "versionId="
        + versionId
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

  /** Builder class for constructing {@link BlobProofBundle} instances. */
  public static class Builder {
    private int versionId;
    private Blob blob;
    private KZGCommitment kzgCommitment;
    private List<KZGProof> kzgProof;
    private VersionedHash versionedHash;

    /** Default constructor for the builder. */
    public Builder() {}

    /**
     * Sets the version ID.
     *
     * @param versionId the version ID.
     * @return the builder instance.
     */
    public Builder versionId(final int versionId) {
      this.versionId = versionId;
      return this;
    }

    /**
     * Sets the blob.
     *
     * @param blob the blob.
     * @return the builder instance.
     */
    public Builder blob(final Blob blob) {
      this.blob = blob;
      return this;
    }

    /**
     * Sets the KZG commitment.
     *
     * @param kzgCommitment the KZG commitment.
     * @return the builder instance.
     */
    public Builder kzgCommitment(final KZGCommitment kzgCommitment) {
      this.kzgCommitment = kzgCommitment;
      return this;
    }

    /**
     * Sets the KZG proof.
     *
     * @param kzgProof the KZG proof.
     * @return the builder instance.
     */
    public Builder kzgProof(final List<KZGProof> kzgProof) {
      this.kzgProof = kzgProof;
      return this;
    }

    /**
     * Sets the versioned hash.
     *
     * @param versionedHash the versioned hash.
     * @return the builder instance.
     */
    public Builder versionedHash(final VersionedHash versionedHash) {
      this.versionedHash = versionedHash;
      return this;
    }

    /**
     * Builds a {@link BlobProofBundle} instance.
     *
     * @return the constructed {@link BlobProofBundle}.
     * @throws IllegalStateException if required fields are missing or invalid.
     */
    public BlobProofBundle build() {
      checkState(kzgCommitment != null, "kzgCommitment must not be empty");
      checkState(versionedHash != null, "versionedHash must not be empty");
      checkState(blob != null, "blob must not be empty");
      checkState(kzgProof != null, "kzgProof must not be empty");
      if (versionId == 0 && kzgProof.size() != 1) {
        throw new IllegalStateException("'Invalid kzgProof' size for versionId 0.");
      }
      if (versionId == 1 && kzgProof.size() != CELL_PROOFS_PER_BLOB) {
        throw new IllegalStateException("'Invalid kzgProof' size for versionId 1.");
      }
      return new BlobProofBundle(versionId, blob, kzgCommitment, kzgProof, versionedHash);
    }
  }
}
