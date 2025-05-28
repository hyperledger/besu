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

import static org.hyperledger.besu.ethereum.core.kzg.BlobProofBundle.CELL_PROOFS_PER_BLOB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.BlobType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.kzg.Blob;
import org.hyperledger.besu.ethereum.core.kzg.BlobsWithCommitments;
import org.hyperledger.besu.ethereum.core.kzg.KZGCommitment;
import org.hyperledger.besu.ethereum.core.kzg.KZGProof;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MainnetBlobsValidatorTest {

  private MainnetBlobsValidator validator;
  private Transaction mockTransaction;
  private BlobsWithCommitments mockBlobsWithCommitments;

  @BeforeEach
  public void setUp() {
    validator = new MainnetBlobsValidator(Set.of(BlobType.KZG_PROOF, BlobType.KZG_CELL_PROOFS));
    mockTransaction = mock(Transaction.class);
    mockBlobsWithCommitments = mock(BlobsWithCommitments.class);
  }

  @Test
  public void shouldFailValidateTransactionsBlobs_EmptyBlobs() {
    when(mockTransaction.getBlobsWithCommitments()).thenReturn(Optional.empty());

    ValidationResult<TransactionInvalidReason> result =
        validator.validateTransactionsBlobs(mockTransaction);

    assertFalse(result.isValid());
    assertEquals(TransactionInvalidReason.INVALID_BLOBS, result.getInvalidReason());
    assertEquals(
        "transaction blobs are empty, cannot verify without blobs", result.getErrorMessage());
  }

  @Test
  public void shouldFailValidateTransactionsBlobs_MismatchedBlobAndCommitmentSizes() {
    when(mockTransaction.getBlobsWithCommitments())
        .thenReturn(Optional.of(mockBlobsWithCommitments));
    when(mockBlobsWithCommitments.getBlobType())
        .thenReturn(BlobType.KZG_CELL_PROOFS); // Valid version
    when(mockBlobsWithCommitments.getBlobs()).thenReturn(List.of(mock(Blob.class)));
    when(mockBlobsWithCommitments.getKzgCommitments()).thenReturn(List.of()); // Empty commitments

    ValidationResult<TransactionInvalidReason> result =
        validator.validateTransactionsBlobs(mockTransaction);

    assertFalse(result.isValid());
    assertEquals(TransactionInvalidReason.INVALID_BLOBS, result.getInvalidReason());
    assertEquals(
        "transaction blobs and commitments are not the same size", result.getErrorMessage());
  }

  @Test
  public void shouldFailValidateTransactionsBlobs_EmptyVersionedHashes() {
    when(mockTransaction.getBlobsWithCommitments())
        .thenReturn(Optional.of(mockBlobsWithCommitments));
    when(mockBlobsWithCommitments.getBlobType())
        .thenReturn(BlobType.KZG_CELL_PROOFS); // Valid version
    when(mockBlobsWithCommitments.getBlobs()).thenReturn(List.of(mock(Blob.class)));
    when(mockBlobsWithCommitments.getKzgCommitments())
        .thenReturn(List.of(mock(KZGCommitment.class)));
    when(mockTransaction.getVersionedHashes()).thenReturn(Optional.empty());

    ValidationResult<TransactionInvalidReason> result =
        validator.validateTransactionsBlobs(mockTransaction);

    assertFalse(result.isValid());
    assertEquals(TransactionInvalidReason.INVALID_BLOBS, result.getInvalidReason());
    assertEquals(
        "transaction versioned hashes are empty, cannot verify without versioned hashes",
        result.getErrorMessage());
  }

  @Test
  public void shouldThrowExceptionWhenKzgCommitmentsSizeIsInvalid_V0() {
    List<Blob> blobs = List.of(mock(Blob.class), mock(Blob.class));
    List<KZGCommitment> kzgCommitments = List.of(mock(KZGCommitment.class));
    List<KZGProof> kzgProofs = List.of(mock(KZGProof.class), mock(KZGProof.class));
    List<VersionedHash> versionedHashes =
        List.of(mock(VersionedHash.class), mock(VersionedHash.class));

    InvalidParameterException exception =
        assertThrows(
            InvalidParameterException.class,
            () ->
                new BlobsWithCommitments(
                    BlobType.KZG_PROOF, kzgCommitments, blobs, kzgProofs, versionedHashes));

    assertEquals("Invalid number of kzgCommitments, expected 2, got 1", exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenVersionedHashSizeIsInvalid_V0() {
    List<Blob> blobs = List.of(mock(Blob.class), mock(Blob.class));
    List<KZGCommitment> kzgCommitments =
        List.of(mock(KZGCommitment.class), mock(KZGCommitment.class));
    List<KZGProof> kzgProofs = List.of(mock(KZGProof.class), mock(KZGProof.class));
    List<VersionedHash> versionedHashes = List.of(mock(VersionedHash.class));

    InvalidParameterException exception =
        assertThrows(
            InvalidParameterException.class,
            () ->
                new BlobsWithCommitments(
                    BlobType.KZG_PROOF, kzgCommitments, blobs, kzgProofs, versionedHashes));

    assertEquals("Invalid number of versionedHashes, expected 2, got 1", exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenKzgProofsSizeIsInvalid_V0() {
    List<Blob> blobs = List.of(mock(Blob.class), mock(Blob.class));
    List<KZGCommitment> kzgCommitments =
        List.of(mock(KZGCommitment.class), mock(KZGCommitment.class));
    List<KZGProof> kzgProofs = List.of(mock(KZGProof.class));
    List<VersionedHash> versionedHashes =
        List.of(mock(VersionedHash.class), mock(VersionedHash.class));

    InvalidParameterException exception =
        assertThrows(
            InvalidParameterException.class,
            () ->
                new BlobsWithCommitments(
                    BlobType.KZG_PROOF, kzgCommitments, blobs, kzgProofs, versionedHashes));

    assertEquals("Invalid number of kzgProofs, expected 2, got 1", exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenKzgCellProofsIsInvalid_V0() {
    List<Blob> blobs = List.of(mock(Blob.class), mock(Blob.class));
    List<KZGCommitment> kzgCommitments =
        List.of(mock(KZGCommitment.class), mock(KZGCommitment.class));
    List<KZGProof> kzgProofs =
        List.of(mock(KZGProof.class), mock(KZGProof.class), mock(KZGProof.class));
    List<VersionedHash> versionedHashes =
        List.of(mock(VersionedHash.class), mock(VersionedHash.class));

    InvalidParameterException exception =
        assertThrows(
            InvalidParameterException.class,
            () ->
                new BlobsWithCommitments(
                    BlobType.KZG_PROOF, kzgCommitments, blobs, kzgProofs, versionedHashes));

    assertEquals("Invalid number of kzgProofs, expected 2, got 3", exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenKzgCommitmentsSizeIsInvalid_V1() {
    List<Blob> blobs = List.of(mock(Blob.class), mock(Blob.class));
    List<KZGCommitment> kzgCommitments = List.of(mock(KZGCommitment.class));
    List<KZGProof> kzgProofs = List.of(mock(KZGProof.class), mock(KZGProof.class));
    List<VersionedHash> versionedHashes =
        List.of(mock(VersionedHash.class), mock(VersionedHash.class));

    InvalidParameterException exception =
        assertThrows(
            InvalidParameterException.class,
            () ->
                new BlobsWithCommitments(
                    BlobType.KZG_CELL_PROOFS, kzgCommitments, blobs, kzgProofs, versionedHashes));

    assertEquals("Invalid number of kzgCommitments, expected 2, got 1", exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenVersionedHashSizeIsInvalid_V1() {
    List<Blob> blobs = List.of(mock(Blob.class), mock(Blob.class));
    List<KZGCommitment> kzgCommitments =
        List.of(mock(KZGCommitment.class), mock(KZGCommitment.class));
    List<KZGProof> kzgProofs = List.of(mock(KZGProof.class), mock(KZGProof.class));
    List<VersionedHash> versionedHashes = List.of(mock(VersionedHash.class));

    InvalidParameterException exception =
        assertThrows(
            InvalidParameterException.class,
            () ->
                new BlobsWithCommitments(
                    BlobType.KZG_CELL_PROOFS, kzgCommitments, blobs, kzgProofs, versionedHashes));

    assertEquals("Invalid number of versionedHashes, expected 2, got 1", exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenProofsSizeIsInvalid_V1() {
    List<Blob> blobs = List.of(mock(Blob.class), mock(Blob.class));
    List<KZGCommitment> kzgCommitments =
        List.of(mock(KZGCommitment.class), mock(KZGCommitment.class));
    List<KZGProof> kzgProofs = List.of(mock(KZGProof.class), mock(KZGProof.class));
    List<VersionedHash> versionedHashes =
        List.of(mock(VersionedHash.class), mock(VersionedHash.class));

    InvalidParameterException exception =
        assertThrows(
            InvalidParameterException.class,
            () ->
                new BlobsWithCommitments(
                    BlobType.KZG_CELL_PROOFS, kzgCommitments, blobs, kzgProofs, versionedHashes));
    assertEquals("Invalid number of cell proofs, expected 256, got 2", exception.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenBlobWithCommitmentsCellProofsSizeIsInvalid_V1() {
    List<Blob> blobs = List.of(mock(Blob.class), mock(Blob.class));
    List<KZGCommitment> kzgCommitments =
        List.of(mock(KZGCommitment.class), mock(KZGCommitment.class));
    List<KZGProof> kzgProofs = List.of(mock(KZGProof.class));
    List<VersionedHash> versionedHashes =
        List.of(mock(VersionedHash.class), mock(VersionedHash.class));

    InvalidParameterException exception =
        assertThrows(
            InvalidParameterException.class,
            () ->
                new BlobsWithCommitments(
                    BlobType.KZG_CELL_PROOFS, kzgCommitments, blobs, kzgProofs, versionedHashes));

    int expectedCellsSize = blobs.size() * CELL_PROOFS_PER_BLOB;
    String error =
        String.format("Invalid number of cell proofs, expected %s, got 1", expectedCellsSize);
    assertEquals(error, exception.getMessage());
  }
}
