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

import java.util.ArrayList;
import java.util.List;

public final class BlobProofBundle {
  private final int versionId;
  private final Blob blob;
  private final KZGCommitment kzgCommitment;
  private final KZGProof kzgProof;
  private final List<KZGProof> kzgCellProof;
  private final VersionedHash versionedHash;

  /**
   * @param versionId version id for the sidecar
   * @param blob The blob
   * @param kzgCommitment The commitment
   * @param kzgProof The proof
   * @param kzgCellProof The cell proof
   * @param versionedHash The versioned hash
   */
  private BlobProofBundle(
      final int versionId,
      final Blob blob,
      final KZGCommitment kzgCommitment,
      final KZGProof kzgProof,
      final List<KZGProof> kzgCellProof,
      final VersionedHash versionedHash) {
    this.versionId = versionId;
    this.blob = blob;
    this.kzgCommitment = kzgCommitment;
    this.kzgProof = kzgProof;
    this.kzgCellProof = kzgCellProof;
    this.versionedHash = versionedHash;
  }

  public int versionId() {
    return versionId;
  }

  public Blob blob() {
    return blob;
  }

  public KZGCommitment kzgCommitment() {
    return kzgCommitment;
  }

  public KZGProof kzgProof() {
    return kzgProof;
  }

  public List<KZGProof> kzgCellProof() {
    return kzgCellProof;
  }

  public VersionedHash versionedHash() {
    return versionedHash;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for BlobProofBundle. */
  public static class Builder {
    private int versionId;
    private Blob blob;
    private KZGCommitment kzgCommitment;
    private KZGProof kzgProof;
    private List<KZGProof> kzgCellProof = new ArrayList<>();
    private VersionedHash versionedHash;

    public Builder versionId(final int versionId) {
      this.versionId = versionId;
      return this;
    }

    public Builder blob(final Blob blob) {
      this.blob = blob;
      return this;
    }

    public Builder kzgCommitment(final KZGCommitment kzgCommitment) {
      this.kzgCommitment = kzgCommitment;
      return this;
    }

    public Builder kzgProof(final KZGProof kzgProof) {
      this.kzgProof = kzgProof;
      return this;
    }

    public Builder kzgCellProof(final List<KZGProof> kzgCellProof) {
      this.kzgCellProof = kzgCellProof;
      return this;
    }

    public Builder versionedHash(final VersionedHash versionedHash) {
      this.versionedHash = versionedHash;
      return this;
    }

    public BlobProofBundle build() {
      checkState(kzgCommitment != null, "kzgCommitment must not be empty");
      checkState(versionedHash != null, "versionedHash must not be empty");
      checkState(blob != null, "kzgCommitment must not be empty");
      if (versionId == 0 && kzgCellProof != null && !kzgCellProof.isEmpty()) {
        throw new IllegalStateException("'kzgCellProof' must be empty when 'versionId' is 0.");
      }
      if (versionId == 1 && kzgProof != null) {
        throw new IllegalStateException("'kzgProof' must be empty when 'versionId' is 1.");
      }
      return new BlobProofBundle(
          versionId, blob, kzgCommitment, kzgProof, kzgCellProof, versionedHash);
    }
  }
}
