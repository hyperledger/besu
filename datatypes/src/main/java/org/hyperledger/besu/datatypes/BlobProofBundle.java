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

import static com.google.common.base.Preconditions.checkState;

import java.util.List;

/**
 * Represents a bundle of proofs for a blob, including KZG commitments and proofs.
 *
 * @param versionId the version ID of the bundle.
 * @param blob the blob being proven.
 * @param kzgCommitment the KZG commitment for the blob.
 * @param kzgProof the KZG proof for the blob.
 * @param kzgCellProof the KZG cell proof for the blob.
 * @param versionedHash the versioned hash of the blob.
 */
public record BlobProofBundle(
    int versionId,
    Blob blob,
    KZGCommitment kzgCommitment,
    KZGProof kzgProof,
    List<KZGCellProof> kzgCellProof,
    VersionedHash versionedHash) {

  /** Version ID for KZG proofs. */
  public static final int VERSION_0_KZG_PROOFS = 0;

  /** Version ID for KZG cell proofs. */
  public static final int VERSION_1_KZG_CELL_PROOFS = 1;

  /** Number of cell proofs per blob. */
  public static final int CELL_PROOFS_PER_BLOB = 128;

  /**
   * Creates a new {@link Builder} instance for constructing a {@link BlobProofBundle}.
   *
   * @return a new {@link Builder}.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for constructing {@link BlobProofBundle} instances. */
  public static class Builder {
    private int versionId;
    private Blob blob;
    private KZGCommitment kzgCommitment;
    private KZGProof kzgProof;
    private List<KZGCellProof> kzgCellProof;
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
    public Builder kzgProof(final KZGProof kzgProof) {
      this.kzgProof = kzgProof;
      return this;
    }

    /**
     * Sets the list of KZG cell proofs.
     *
     * @param kzgCellProof the list of KZG cell proofs.
     * @return the builder instance.
     */
    public Builder kzgCellProof(final List<KZGCellProof> kzgCellProof) {
      this.kzgCellProof = kzgCellProof;
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
      if (versionId == 0 && kzgCellProof != null) {
        throw new IllegalStateException("'kzgCellProof' must be empty when 'versionId' is 0.");
      }
      if (versionId == 0 && kzgProof == null) {
        throw new IllegalStateException("'kzgProof' must not be empty when 'versionId' is 0.");
      }
      if (versionId == 1 && kzgProof != null) {
        throw new IllegalStateException("'kzgProof' must be empty when 'versionId' is 1.");
      }
      if (versionId == 1 && kzgCellProof == null) {
        throw new IllegalStateException("'kzgCellProof' must not be empty when 'versionId' is 1.");
      }
      return new BlobProofBundle(
          versionId, blob, kzgCommitment, kzgProof, kzgCellProof, versionedHash);
    }
  }
}
