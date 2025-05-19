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
package org.hyperledger.besu.ethereum.mainnet;

import static org.hyperledger.besu.datatypes.BlobProofBundle.CELL_PROOFS_PER_BLOB;
import static org.hyperledger.besu.datatypes.BlobProofBundle.VERSION_0_KZG_PROOFS;
import static org.hyperledger.besu.datatypes.BlobProofBundle.VERSION_1_KZG_CELL_PROOFS;

import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.BlobProofBundle;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.KZGCommitment;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.digests.SHA256Digest;

public class MainnetBlobsValidator {
  final Set<Integer> acceptedBlobVersions;

  public MainnetBlobsValidator(final Set<Integer> acceptedBlobVersions) {
    this.acceptedBlobVersions = acceptedBlobVersions;
  }

  public ValidationResult<TransactionInvalidReason> validateTransactionsBlobs(
      final Transaction transaction) {

    if (transaction.getBlobsWithCommitments().isEmpty()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction blobs are empty, cannot verify without blobs");
    }
    BlobsWithCommitments blobsWithCommitments = transaction.getBlobsWithCommitments().get();
    if (!acceptedBlobVersions.contains(blobsWithCommitments.getVersionId())) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS, "invalid blob version");
    }

    if (blobsWithCommitments.getBlobs().size() != blobsWithCommitments.getKzgCommitments().size()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction blobs and commitments are not the same size");
    }

    if (transaction.getVersionedHashes().isEmpty()) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction versioned hashes are empty, cannot verify without versioned hashes");
    }
    final List<VersionedHash> versionedHashes = transaction.getVersionedHashes().get();

    for (int i = 0; i < versionedHashes.size(); i++) {
      final KZGCommitment commitment = blobsWithCommitments.getKzgCommitments().get(i);
      final VersionedHash versionedHash = versionedHashes.get(i);
      if (versionedHash.getVersionId() != VersionedHash.SHA256_VERSION_ID) {
        return ValidationResult.invalid(
            TransactionInvalidReason.INVALID_BLOBS,
            "transaction blobs commitment version is not supported. Expected "
                + VersionedHash.SHA256_VERSION_ID
                + ", found "
                + versionedHash.getVersionId());
      }

      final VersionedHash calculatedVersionedHash = hashCommitment(commitment);
      if (!calculatedVersionedHash.equals(versionedHash)) {
        return ValidationResult.invalid(
            TransactionInvalidReason.INVALID_BLOBS,
            "transaction blobs commitment hash does not match commitment");
      }
    }

    if (blobsWithCommitments.getVersionId() == VERSION_0_KZG_PROOFS) {
      return validateBlobWithProof(blobsWithCommitments);
    }

    if (blobsWithCommitments.getVersionId() == VERSION_1_KZG_CELL_PROOFS) {
      return validateBlobWithCellProof(blobsWithCommitments);
    }
    return ValidationResult.valid();
  }

  private ValidationResult<TransactionInvalidReason> validateBlobWithProof(
      final BlobsWithCommitments blobsWithCommitments) {
    final byte[] blobs =
        Bytes.wrap(blobsWithCommitments.getBlobs().stream().map(Blob::getData).toList())
            .toArrayUnsafe();
    final byte[] kzgCommitments =
        Bytes.wrap(
                blobsWithCommitments.getKzgCommitments().stream()
                    .map(kc -> (Bytes) kc.getData())
                    .toList())
            .toArrayUnsafe();
    final byte[] kzgProofs =
        Bytes.wrap(
                blobsWithCommitments.getKzgProofs().stream()
                    .map(kp -> (Bytes) kp.getData())
                    .toList())
            .toArrayUnsafe();
    final boolean kzgVerification =
        CKZG4844JNI.verifyBlobKzgProofBatch(
            blobs, kzgCommitments, kzgProofs, blobsWithCommitments.getBlobs().size());
    if (!kzgVerification) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction blobs kzg proof verification failed");
    }
    return ValidationResult.valid();
  }

  private ValidationResult<TransactionInvalidReason> validateBlobWithCellProof(
      final BlobsWithCommitments blobsWithCommitments) {
    final Bytes kzgCellProofs =
        Bytes.wrap(
            blobsWithCommitments.getKzgCellProofs().stream()
                .map(kp -> (Bytes) kp.getData())
                .toList());

    final byte[] commitments = extendCommitments(blobsWithCommitments.getKzgCommitments());
    long[] cellIndices = new long[CELL_PROOFS_PER_BLOB * blobsWithCommitments.getBlobs().size()];
    List<Bytes> cells = new ArrayList<>();
    for (int blobIndex = 0; blobIndex < blobsWithCommitments.getBlobs().size(); blobIndex++) {
      byte[] blobCells =
          CKZG4844JNI.computeCells(
              blobsWithCommitments.getBlobs().get(blobIndex).getData().toArray());
      if (blobCells == null) {
        return ValidationResult.invalid(
            TransactionInvalidReason.INVALID_BLOBS, "error computing cells for blob");
      }
      cells.add(Bytes.wrap(blobCells));
      for (int index = 0; index < CELL_PROOFS_PER_BLOB; index++) {
        int cellIndex = blobIndex * CELL_PROOFS_PER_BLOB + index;
        cellIndices[cellIndex] = index;
      }
    }
    var cellsByteArray = Bytes.wrap(cells.stream().toList()).toArrayUnsafe();
    final boolean kzgVerification =
        CKZG4844JNI.verifyCellKzgProofBatch(
            commitments, cellIndices, cellsByteArray, kzgCellProofs.toArrayUnsafe());
    if (!kzgVerification) {
      return ValidationResult.invalid(
          TransactionInvalidReason.INVALID_BLOBS,
          "transaction blobs kzg cell proof verification failed");
    }
    return ValidationResult.valid();
  }

  private static byte[] extendCommitments(final List<KZGCommitment> commitments) {
    int newSize = commitments.size() * BlobProofBundle.CELL_PROOFS_PER_BLOB;
    ArrayList<KZGCommitment> extendedCommitments = new ArrayList<>(newSize);
    for (KZGCommitment kzgCommitment : commitments) {
      for (int i = 0; i < BlobProofBundle.CELL_PROOFS_PER_BLOB; i++) {
        extendedCommitments.add(new KZGCommitment(kzgCommitment.getData()));
      }
    }
    return Bytes.wrap(extendedCommitments.stream().map(kc -> (Bytes) kc.getData()).toList())
        .toArrayUnsafe();
  }

  public static VersionedHash hashCommitment(final KZGCommitment commitment) {
    final SHA256Digest digest = new SHA256Digest();
    digest.update(commitment.getData().toArrayUnsafe(), 0, commitment.getData().size());

    final byte[] dig = new byte[digest.getDigestSize()];

    digest.doFinal(dig, 0);

    dig[0] = VersionedHash.SHA256_VERSION_ID;
    return new VersionedHash(Bytes32.wrap(dig));
  }
}
