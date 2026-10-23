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
package org.hyperledger.besu.plugins.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.TransactionLocation;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.TransactionAdapter;

import java.util.List;
import java.util.Optional;

import graphql.schema.DataFetcher;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionDataFetcherTest extends AbstractDataFetcherTest {

  private DataFetcher<Optional<TransactionAdapter>> fetcher;
  final Hash fakedHash = Hash.hash(Bytes.fromBase64String("ThisIsAFakeHash"));
  final Hash fakedParentHash = Hash.hash(Bytes.of(2));
  final VersionedHash fakeVersionedHash =
      new VersionedHash(VersionedHash.SHA256_VERSION_ID, fakedHash);
  final long blobGasUsed = 127 * 1024L;
  final Wei blobGasPrice = Wei.of(128);
  final Wei maxFeePerBlobGas = Wei.of(1280);

  @Mock TransactionLocation transactionLocation;

  @BeforeEach
  @Override
  public void before() {
    super.before();
    fetcher = fetchers.getTransactionDataFetcher();
  }

  // The transaction is the only one of the block fakedHash, and its receipt is transactionReceipt.
  private void stubTransactionInBlock() {
    when(transactionLocation.getBlockHash()).thenReturn(fakedHash);
    when(transactionLocation.getTransactionIndex()).thenReturn(0);
    when(blockchainService.getTransactionLocation(any()))
        .thenReturn(Optional.of(transactionLocation));
    when(blockchainService.getBlockHeaderByHash(fakedHash)).thenReturn(Optional.of(header));
    when(blockchainService.getTransactionByHash(any())).thenReturn(Optional.of(transaction));
    when(blockchainService.getBlockByHash(fakedHash)).thenReturn(Optional.of(blockContext));
    when(blockContext.getBlockHeader()).thenReturn(header);
    when(blockContext.getBlockBody()).thenReturn(body);
    doReturn(List.of(transaction)).when(body).getTransactions();
    when(blockchainService.getReceiptsByBlockHash(fakedHash))
        .thenReturn(Optional.of(List.of(transactionReceipt)));
    when(transaction.getHash()).thenReturn(fakedHash);
  }

  // A blob transaction's blob gas price is computed from its parent block.
  private void stubBlobGasPrice(final Wei price) {
    when(header.getParentHash()).thenReturn(fakedParentHash);
    when(blockchainService.getBlockHeaderByHash(fakedParentHash)).thenReturn(Optional.of(header));
    when(blockchainService.getBlobGasPrice(header)).thenReturn(price);
  }

  @Test
  void emptyBlobs() throws Exception {
    when(environment.getArgument("hash")).thenReturn(fakedHash);

    stubTransactionInBlock();
    when(transaction.getType()).thenReturn(TransactionType.EIP1559);
    when(transaction.getVersionedHashes()).thenReturn(Optional.empty());
    when(transaction.getMaxFeePerBlobGas()).thenReturn(Optional.empty());

    var transactionData = fetcher.get(environment);
    assertThat(transactionData).isPresent();
    assertThat(transactionData.get().getBlobVersionedHashes()).isEmpty();
    assertThat(transactionData.get().getBlobGasUsed(environment)).isEmpty();
    assertThat(transactionData.get().getBlobGasPrice(environment)).isEmpty();
    assertThat(transactionData.get().getMaxFeePerBlobGas()).isEmpty();
  }

  @Test
  void hasZeroBlobs() throws Exception {
    when(environment.getArgument("hash")).thenReturn(fakedHash);

    stubTransactionInBlock();
    when(transaction.getType()).thenReturn(TransactionType.BLOB);
    when(transaction.getVersionedHashes()).thenReturn(Optional.of(List.of()));
    doReturn(Optional.of(Wei.ZERO)).when(transaction).getMaxFeePerBlobGas();
    when(blockchainService.getBlobGasCost(header, 0)).thenReturn(0L);
    stubBlobGasPrice(Wei.ZERO);

    var transactionData = fetcher.get(environment);
    assertThat(transactionData).isPresent();
    assertThat(transactionData.get().getBlobVersionedHashes()).isEmpty();
    assertThat(transactionData.get().getBlobGasUsed(environment)).contains(0L);
    assertThat(transactionData.get().getBlobGasPrice(environment)).contains(Wei.ZERO);
    assertThat(transactionData.get().getMaxFeePerBlobGas()).contains(Wei.ZERO);
  }

  @Test
  void hasOneBlob() throws Exception {
    when(environment.getArgument("hash")).thenReturn(fakedHash);

    stubTransactionInBlock();
    when(transaction.getType()).thenReturn(TransactionType.BLOB);
    when(transaction.getVersionedHashes()).thenReturn(Optional.of(List.of(fakeVersionedHash)));
    doReturn(Optional.of(maxFeePerBlobGas)).when(transaction).getMaxFeePerBlobGas();
    when(blockchainService.getBlobGasCost(header, 1)).thenReturn(blobGasUsed);
    stubBlobGasPrice(blobGasPrice);

    var transactionData = fetcher.get(environment);
    assertThat(transactionData).isPresent();
    assertThat(transactionData.get().getBlobVersionedHashes()).containsExactly(fakeVersionedHash);
    assertThat(transactionData.get().getBlobGasUsed(environment)).contains(blobGasUsed);
    assertThat(transactionData.get().getBlobGasPrice(environment)).contains(blobGasPrice);
    assertThat(transactionData.get().getMaxFeePerBlobGas()).contains(maxFeePerBlobGas);
  }

  @Test
  void blobGasPriceIsEmptyWithoutParentBlock() throws Exception {
    // As in Besu, the blob gas price is unknown when the parent block is not available.
    when(environment.getArgument("hash")).thenReturn(fakedHash);

    stubTransactionInBlock();
    when(transaction.getType()).thenReturn(TransactionType.BLOB);
    when(transaction.getVersionedHashes()).thenReturn(Optional.of(List.of(fakeVersionedHash)));
    when(blockchainService.getBlobGasCost(header, 1)).thenReturn(blobGasUsed);
    when(header.getParentHash()).thenReturn(fakedParentHash);
    when(blockchainService.getBlockHeaderByHash(fakedParentHash)).thenReturn(Optional.empty());

    var transactionData = fetcher.get(environment);
    assertThat(transactionData).isPresent();
    assertThat(transactionData.get().getBlobGasUsed(environment)).contains(blobGasUsed);
    assertThat(transactionData.get().getBlobGasPrice(environment)).isEmpty();
  }
}
