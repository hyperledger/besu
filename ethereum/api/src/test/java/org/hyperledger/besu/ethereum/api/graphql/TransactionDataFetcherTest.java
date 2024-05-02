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
package org.hyperledger.besu.ethereum.api.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.graphql.internal.pojoadapter.TransactionAdapter;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;

import java.util.List;
import java.util.Optional;

import graphql.schema.DataFetcher;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionDataFetcherTest extends AbstractDataFetcherTest {

  private DataFetcher<Optional<TransactionAdapter>> fetcher;
  final Hash fakedHash = Hash.hash(Bytes.fromBase64String("ThisIsAFakeHash"));
  final VersionedHash fakeVersionedHash =
      new VersionedHash(VersionedHash.SHA256_VERSION_ID, fakedHash);
  final long blobGasUsed = 127 * 1024L;
  final Wei blobGasPrice = Wei.of(128);
  final Wei maxFeePerBlobGas = Wei.of(1280);

  @BeforeEach
  @Override
  public void before() {
    super.before();
    fetcher = fetchers.getTransactionDataFetcher();
  }

  @Test
  void emptyBlobs() throws Exception {
    when(environment.getArgument("hash")).thenReturn(fakedHash);

    when(graphQLContext.get(GraphQLContextType.BLOCKCHAIN_QUERIES)).thenReturn(query);

    TransactionWithMetadata transactionWithMetadata = new TransactionWithMetadata(transaction);
    when(query.transactionByHash(any())).thenReturn(Optional.of(transactionWithMetadata));
    when(transaction.getVersionedHashes()).thenReturn(Optional.empty());
    when(transaction.getMaxFeePerBlobGas()).thenReturn(Optional.empty());

    TransactionReceiptWithMetadata transactionReceiptWithMetadata =
        TransactionReceiptWithMetadata.create(
            transactionReceipt,
            transaction,
            fakedHash,
            0,
            21000,
            Optional.empty(),
            fakedHash,
            1,
            Optional.empty(),
            Optional.empty(),
            0);
    when(query.transactionReceiptByTransactionHash(any(), any()))
        .thenReturn(Optional.of(transactionReceiptWithMetadata));

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

    when(graphQLContext.get(GraphQLContextType.BLOCKCHAIN_QUERIES)).thenReturn(query);

    TransactionWithMetadata transactionWithMetadata = new TransactionWithMetadata(transaction);
    when(query.transactionByHash(any())).thenReturn(Optional.of(transactionWithMetadata));
    when(transaction.getVersionedHashes()).thenReturn(Optional.of(List.of()));
    when(transaction.getMaxFeePerBlobGas()).thenReturn(Optional.of(Wei.ZERO));

    TransactionReceiptWithMetadata transactionReceiptWithMetadata =
        TransactionReceiptWithMetadata.create(
            transactionReceipt,
            transaction,
            fakedHash,
            0,
            21000,
            Optional.empty(),
            fakedHash,
            1,
            Optional.of(0L),
            Optional.of(Wei.ZERO),
            0);
    when(query.transactionReceiptByTransactionHash(any(), any()))
        .thenReturn(Optional.of(transactionReceiptWithMetadata));

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

    when(graphQLContext.get(GraphQLContextType.BLOCKCHAIN_QUERIES)).thenReturn(query);

    TransactionWithMetadata transactionWithMetadata = new TransactionWithMetadata(transaction);
    when(query.transactionByHash(any())).thenReturn(Optional.of(transactionWithMetadata));
    when(transaction.getVersionedHashes()).thenReturn(Optional.of(List.of(fakeVersionedHash)));
    when(transaction.getMaxFeePerBlobGas()).thenReturn(Optional.of(maxFeePerBlobGas));

    TransactionReceiptWithMetadata transactionReceiptWithMetadata =
        TransactionReceiptWithMetadata.create(
            transactionReceipt,
            transaction,
            fakedHash,
            0,
            21000,
            Optional.empty(),
            fakedHash,
            1,
            Optional.of(blobGasUsed),
            Optional.of(blobGasPrice),
            0);
    when(query.transactionReceiptByTransactionHash(any(), any()))
        .thenReturn(Optional.of(transactionReceiptWithMetadata));

    var transactionData = fetcher.get(environment);
    assertThat(transactionData).isPresent();
    assertThat(transactionData.get().getBlobVersionedHashes()).containsExactly(fakeVersionedHash);
    assertThat(transactionData.get().getBlobGasUsed(environment)).contains(blobGasUsed);
    assertThat(transactionData.get().getBlobGasPrice(environment)).contains(blobGasPrice);
    assertThat(transactionData.get().getMaxFeePerBlobGas()).contains(maxFeePerBlobGas);
  }
}
