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
package org.hyperledger.besu.ethereum.mainnet.transactionpool;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.kzg.BlobProofBundle;
import org.hyperledger.besu.ethereum.core.kzg.BlobsWithCommitments;
import org.hyperledger.besu.ethereum.core.kzg.KzgHelper;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class OsakaTransactionPoolPreProcessorTest {

  private final OsakaTransactionPoolPreProcessor preProcessor =
      new OsakaTransactionPoolPreProcessor();

  private Transaction mockTransaction;
  private TransactionType mockTransactionType;

  @BeforeEach
  void setup() {
    mockTransaction = mock(Transaction.class);
    mockTransactionType = mock(TransactionType.class);
    when(mockTransaction.getType()).thenReturn(mockTransactionType);
  }

  @Test
  void shouldReturnOriginalIfNotLocal() {
    Transaction result = preProcessor.prepareTransaction(mockTransaction, false);
    assertSame(mockTransaction, result);
  }

  @Test
  void shouldReturnOriginalIfTransactionDoesNotSupportBlobs() {
    when(mockTransactionType.supportsBlob()).thenReturn(false);
    Transaction result = preProcessor.prepareTransaction(mockTransaction, true);
    assertSame(mockTransaction, result);
  }

  @Test
  void shouldReturnOriginalIfNoBlobsPresent() {
    when(mockTransactionType.supportsBlob()).thenReturn(true);
    when(mockTransaction.getBlobsWithCommitments()).thenReturn(Optional.empty());

    Transaction result = preProcessor.prepareTransaction(mockTransaction, true);
    assertSame(mockTransaction, result);
  }

  @Test
  void shouldReturnOriginalIfBlobsAreNotVersion0() {
    BlobsWithCommitments blobs = mock(BlobsWithCommitments.class);
    when(blobs.getVersionId())
        .thenReturn(BlobProofBundle.VERSION_1_KZG_CELL_PROOFS); // Not version 0
    when(mockTransactionType.supportsBlob()).thenReturn(true);
    when(mockTransaction.getBlobsWithCommitments()).thenReturn(Optional.of(blobs));

    Transaction result = preProcessor.prepareTransaction(mockTransaction, true);
    assertSame(mockTransaction, result);
  }

  @Test
  void shouldUpgradeTransactionIfBlobsAreVersion0() {
    BlobsWithCommitments version0Blobs = mock(BlobsWithCommitments.class);
    BlobsWithCommitments upgradedBlobs = mock(BlobsWithCommitments.class);
    Transaction upgradedTransaction = mock(Transaction.class);

    when(version0Blobs.getVersionId()).thenReturn(BlobProofBundle.VERSION_0_KZG_PROOFS);
    when(mockTransactionType.supportsBlob()).thenReturn(true);
    when(mockTransaction.getBlobsWithCommitments()).thenReturn(Optional.of(version0Blobs));

    try (MockedStatic<KzgHelper> kzgHelperMock = mockStatic(KzgHelper.class);
        MockedStatic<Transaction> transactionBuilderMock = mockStatic(Transaction.class)) {

      kzgHelperMock
          .when(() -> KzgHelper.convertToVersion1(version0Blobs))
          .thenReturn(upgradedBlobs);
      Transaction.Builder mockBuilder = mock(Transaction.Builder.class);
      when(mockBuilder.copiedFrom(mockTransaction)).thenReturn(mockBuilder);
      when(mockBuilder.blobsWithCommitments(upgradedBlobs)).thenReturn(mockBuilder);
      when(mockBuilder.build()).thenReturn(upgradedTransaction);

      transactionBuilderMock.when(Transaction::builder).thenReturn(mockBuilder);

      Transaction result = preProcessor.prepareTransaction(mockTransaction, true);
      assertSame(upgradedTransaction, result);
    }
  }
}
