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

package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.KZGCommitment;
import org.hyperledger.besu.datatypes.KZGProof;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.tuweni.bytes.Bytes;

@JsonPropertyOrder({"commitments", "proofs", "blobs"})
public class BlobsBundleV1 {

  private final List<String> commitments;

  private final List<String> proofs;

  private final List<String> blobs;

  public BlobsBundleV1(final List<Transaction> transactions) {
    final List<BlobsWithCommitments> blobsWithCommitments =
        transactions.stream()
            .map(Transaction::getBlobsWithCommitments)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

    this.commitments =
        blobsWithCommitments.stream()
            .flatMap(b -> b.getKzgCommitments().stream())
            .map(KZGCommitment::getData)
            .map(Bytes::toString)
            .collect(Collectors.toList());

    this.proofs =
        blobsWithCommitments.stream()
            .flatMap(b -> b.getKzgProofs().stream())
            .map(KZGProof::getData)
            .map(Bytes::toString)
            .collect(Collectors.toList());

    this.blobs =
        blobsWithCommitments.stream()
            .flatMap(b -> b.getBlobs().stream())
            .map(Blob::getData)
            .map(Bytes::toString)
            .collect(Collectors.toList());
  }

  public BlobsBundleV1(
      final List<String> commitments, final List<String> proofs, final List<String> blobs) {
    if (blobs.size() != commitments.size() || blobs.size() != proofs.size()) {
      throw new InvalidParameterException(
          "There must be an equal number of blobs, commitments and proofs");
    }
    this.commitments = commitments;
    this.proofs = proofs;
    this.blobs = blobs;
  }

  @JsonGetter("commitments")
  public List<String> getCommitments() {
    return commitments;
  }

  @JsonGetter("proofs")
  public List<String> getProofs() {
    return proofs;
  }

  @JsonGetter("blobs")
  public List<String> getBlobs() {
    return blobs;
  }
}
