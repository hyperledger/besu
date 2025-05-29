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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.BlobType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.kzg.Blob;
import org.hyperledger.besu.ethereum.core.kzg.BlobsWithCommitments;
import org.hyperledger.besu.ethereum.core.kzg.KZGCommitment;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;

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
}
