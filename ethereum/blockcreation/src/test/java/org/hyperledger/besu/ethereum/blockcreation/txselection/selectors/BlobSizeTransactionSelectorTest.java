/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.blockcreation.txselection.selectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.BlobsWithCommitments;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.KZGCommitment;
import org.hyperledger.besu.datatypes.KZGProof;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockSelectionContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionEvaluationContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;

import java.util.Optional;
import java.util.stream.IntStream;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlobSizeTransactionSelectorTest {
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance);
  private static final KeyPair KEYS = SIGNATURE_ALGORITHM.get().generateKeyPair();

  private static final long BLOB_GAS_PER_BLOB = new CancunGasCalculator().getBlobGasPerBlob();
  private static final int MAX_BLOBS = 6;
  private static final long MAX_BLOB_GAS = BLOB_GAS_PER_BLOB * MAX_BLOBS;

  @Mock(answer = RETURNS_DEEP_STUBS)
  BlockSelectionContext blockSelectionContext;

  @Mock TransactionSelectionResults selectionResults;

  @Test
  void notBlobTransactionsAreSelectedWithoutAnyCheck() {
    final var selector = new BlobSizeTransactionSelector(blockSelectionContext);

    final var nonBlobTx = createEIP1559PendingTransaction();

    final var txEvaluationContext =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(), nonBlobTx, null, null, null);

    final var result =
        selector.evaluateTransactionPreProcessing(txEvaluationContext, selectionResults);
    assertThat(result).isEqualTo(TransactionSelectionResult.SELECTED);
    verifyNoInteractions(selectionResults);
  }

  @Test
  void firstBlobTransactionIsSelected() {
    when(blockSelectionContext.gasLimitCalculator().currentBlobGasLimit()).thenReturn(MAX_BLOB_GAS);
    when(blockSelectionContext.gasCalculator().blobGasCost(anyLong()))
        .thenAnswer(iom -> BLOB_GAS_PER_BLOB * iom.getArgument(0, Long.class));

    final var selector = new BlobSizeTransactionSelector(blockSelectionContext);

    final var firstBlobTx = createBlobPendingTransaction(MAX_BLOBS);

    final var txEvaluationContext =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(), firstBlobTx, null, null, null);

    when(selectionResults.getCumulativeBlobGasUsed()).thenReturn(0L);

    final var result =
        selector.evaluateTransactionPreProcessing(txEvaluationContext, selectionResults);
    assertThat(result).isEqualTo(TransactionSelectionResult.SELECTED);
    verify(selectionResults).getCumulativeBlobGasUsed();
  }

  @Test
  void returnsBlobsFullWhenMaxNumberOfBlobsAlreadyPresent() {
    when(blockSelectionContext.gasLimitCalculator().currentBlobGasLimit()).thenReturn(MAX_BLOB_GAS);

    final var selector = new BlobSizeTransactionSelector(blockSelectionContext);

    final var firstBlobTx = createBlobPendingTransaction(1);

    final var txEvaluationContext =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(), firstBlobTx, null, null, null);

    when(selectionResults.getCumulativeBlobGasUsed()).thenReturn(MAX_BLOB_GAS);

    final var result =
        selector.evaluateTransactionPreProcessing(txEvaluationContext, selectionResults);
    assertThat(result).isEqualTo(TransactionSelectionResult.BLOBS_FULL);
    verify(selectionResults).getCumulativeBlobGasUsed();
  }

  @Test
  void returnsTooLargeForRemainingBlobGas() {
    when(blockSelectionContext.gasLimitCalculator().currentBlobGasLimit()).thenReturn(MAX_BLOB_GAS);
    when(blockSelectionContext.gasCalculator().blobGasCost(anyLong()))
        .thenAnswer(iom -> BLOB_GAS_PER_BLOB * iom.getArgument(0, Long.class));

    final var selector = new BlobSizeTransactionSelector(blockSelectionContext);

    final var firstBlobTx = createBlobPendingTransaction(MAX_BLOBS);

    final var txEvaluationContext =
        new TransactionEvaluationContext(
            blockSelectionContext.pendingBlockHeader(), firstBlobTx, null, null, null);

    when(selectionResults.getCumulativeBlobGasUsed()).thenReturn(MAX_BLOB_GAS - 1);

    final var result =
        selector.evaluateTransactionPreProcessing(txEvaluationContext, selectionResults);
    assertThat(result).isEqualTo(TransactionSelectionResult.TX_TOO_LARGE_FOR_REMAINING_BLOB_GAS);
    verify(selectionResults).getCumulativeBlobGasUsed();
  }

  private PendingTransaction createEIP1559PendingTransaction() {
    return PendingTransaction.newPendingTransaction(
        createTransaction(TransactionType.EIP1559, 0), false, false);
  }

  private PendingTransaction createBlobPendingTransaction(final int blobCount) {
    return PendingTransaction.newPendingTransaction(
        createTransaction(TransactionType.BLOB, blobCount), false, false);
  }

  private Transaction createTransaction(final TransactionType type, final int blobCount) {

    var tx =
        new TransactionTestFixture()
            .to(Optional.of(Address.fromHexString("0x634316eA0EE79c701c6F67C53A4C54cBAfd2316d")))
            .nonce(0)
            .type(type);
    tx.maxFeePerGas(Optional.of(Wei.of(1000))).maxPriorityFeePerGas(Optional.of(Wei.of(100)));
    if (type.supportsBlob()) {
      if (blobCount > 0) {
        tx.maxFeePerBlobGas(Optional.of(Wei.of(10)));
        final var versionHashes =
            IntStream.range(0, blobCount)
                .mapToObj(i -> new VersionedHash((byte) 1, Hash.ZERO))
                .toList();
        final var kgzCommitments =
            IntStream.range(0, blobCount)
                .mapToObj(i -> new KZGCommitment(Bytes48.random()))
                .toList();
        final var kzgProofs =
            IntStream.range(0, blobCount).mapToObj(i -> new KZGProof(Bytes48.random())).toList();
        final var blobs =
            IntStream.range(0, blobCount).mapToObj(i -> new Blob(Bytes.random(32 * 4096))).toList();
        tx.versionedHashes(Optional.of(versionHashes));
        final var blobsWithCommitments =
            new BlobsWithCommitments(kgzCommitments, blobs, kzgProofs, versionHashes);
        tx.blobsWithCommitments(Optional.of(blobsWithCommitments));
      } else {
        fail("At least 1 blob is required for blob tx type");
      }
    }
    return tx.createTransaction(KEYS);
  }
}
