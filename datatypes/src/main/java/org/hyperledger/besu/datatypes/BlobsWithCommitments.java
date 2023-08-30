/*
 * Copyright Hyperledger Besu Contributors.
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
import java.util.List;

/** A class to hold the blobs, commitments, proofs and versioned hashes for a set of blobs. */
public class BlobsWithCommitments {
  private final List<KZGCommitment> kzgCommitments;
  private final List<Blob> blobs;
  private final List<KZGProof> kzgProofs;

  private final List<VersionedHash> versionedHashes;

  /**
   * A class to hold the blobs, commitments and proofs for a set of blobs.
   *
   * @param kzgCommitments commitments for the blobs
   * @param blobs list of blobs to be committed to
   * @param kzgProofs proofs for the commitments
   * @param versionedHashes hashes of the commitments
   */
  public BlobsWithCommitments(
      final List<KZGCommitment> kzgCommitments,
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs,
      final List<VersionedHash> versionedHashes) {
    if (blobs.size() == 0) {
      throw new InvalidParameterException(
          "There needs to be a minimum of one blob in a blob transaction with commitments");
    }
    if (blobs.size() != kzgCommitments.size()
        || blobs.size() != kzgProofs.size()
        || blobs.size() != versionedHashes.size()) {
      throw new InvalidParameterException(
          "There must be an equal number of blobs, commitments, proofs, and versioned hashes");
    }
    this.kzgCommitments = kzgCommitments;
    this.blobs = blobs;
    this.kzgProofs = kzgProofs;
    this.versionedHashes = versionedHashes;
  }

  /**
   * Get the blobs.
   *
   * @return the blobs
   */
  public List<Blob> getBlobs() {
    return blobs;
  }

  /**
   * Get the commitments.
   *
   * @return the commitments
   */
  public List<KZGCommitment> getKzgCommitments() {
    return kzgCommitments;
  }

  /**
   * Get the proofs.
   *
   * @return the proofs
   */
  public List<KZGProof> getKzgProofs() {
    return kzgProofs;
  }

  /**
   * Get the hashes.
   *
   * @return the hashes
   */
  public List<VersionedHash> getVersionedHashes() {
    return versionedHashes;
  }
}
