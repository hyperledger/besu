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
package org.hyperledger.besu.ethereum.core.blobs;

import java.security.InvalidParameterException;
import java.util.List;

public class BlobsWithCommitments {
  private final List<KZGCommitment> kzgCommitments;
  private final List<Blob> blobs;
  private final List<KZGProof> kzgProofs;

  public BlobsWithCommitments(
      final List<KZGCommitment> kzgCommitments,
      final List<Blob> blobs,
      final List<KZGProof> kzgProofs) {
    if (blobs.size() != kzgCommitments.size() || blobs.size() != kzgProofs.size()) {
      throw new InvalidParameterException(
          "There must be an equal number of blobs, commitments and proofs");
    }
    this.kzgCommitments = kzgCommitments;
    this.blobs = blobs;
    this.kzgProofs = kzgProofs;
  }

  public List<Blob> getBlobs() {
    return blobs;
  }

  public List<KZGCommitment> getKzgCommitments() {
    return kzgCommitments;
  }

  public List<KZGProof> getKzgProofs() {
    return kzgProofs;
  }
}
