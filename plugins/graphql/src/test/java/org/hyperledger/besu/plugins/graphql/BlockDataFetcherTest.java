/*
 * Copyright ConsenSys AG.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugins.graphql.internal.pojoadapter.NormalBlockAdapter;

import java.util.Optional;

import graphql.schema.DataFetcher;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockDataFetcherTest extends AbstractDataFetcherTest {

  private DataFetcher<Optional<NormalBlockAdapter>> fetcher;

  @BeforeEach
  @Override
  public void before() {
    super.before();
    fetcher = fetchers.getBlockDataFetcher();
  }

  private void stubBlockByNumberPresent() {
    when(blockchainService.getBlockByNumber(ArgumentMatchers.anyLong()))
        .thenReturn(Optional.of(blockContext));
    when(blockContext.getBlockHeader()).thenReturn(header);
    when(blockchainService.getTotalDifficulty(ArgumentMatchers.any()))
        .thenReturn(Optional.of(UInt256.ZERO));
  }

  @Test
  void bothNumberAndHashThrows() {
    final Hash fakedHash = Hash.hash(Bytes.of(1));
    when(environment.getArgument("number")).thenReturn(1L);
    when(environment.getArgument("hash")).thenReturn(fakedHash);

    assertThatThrownBy(() -> fetcher.get(environment)).isInstanceOf(GraphQLException.class);
  }

  @Test
  void onlyNumber() throws Exception {

    when(environment.getArgument("number")).thenReturn(1L);
    when(environment.getArgument("hash")).thenReturn(null);

    when(environment.getGraphQlContext()).thenReturn(graphQLContext);
    stubBlockByNumberPresent();

    assertThat(fetcher.get(environment)).isNotEmpty();
  }

  @Test
  void blockWithoutTotalDifficultyIsNotFound() {
    // Besu reports a block whose total difficulty is not stored as not found.
    when(environment.getArgument("number")).thenReturn(1L);
    when(environment.getArgument("hash")).thenReturn(null);

    when(blockchainService.getBlockByNumber(ArgumentMatchers.anyLong()))
        .thenReturn(Optional.of(blockContext));
    when(blockContext.getBlockHeader()).thenReturn(header);
    when(blockchainService.getTotalDifficulty(ArgumentMatchers.any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> fetcher.get(environment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Block number 1 was not found");
  }

  @Test
  void ibftMiner() throws Exception {
    // IBFT can mine blocks with a coinbase that is an empty account, hence not stored and returned
    // as null. The compromise is to report zeros and empty on query from a block.
    final Address testAddress = Address.fromHexString("0xdeadbeef");

    when(environment.getArgument("number")).thenReturn(1L);
    when(environment.getArgument("hash")).thenReturn(null);

    when(environment.getGraphQlContext()).thenReturn(graphQLContext);
    stubBlockByNumberPresent();
    when(header.getCoinbase()).thenReturn(testAddress);

    final Optional<NormalBlockAdapter> maybeBlock = fetcher.get(environment);
    assertThat(maybeBlock).isPresent();
    assertThat(maybeBlock.get().getMiner(environment)).isNotNull();
    assertThat(maybeBlock.get().getMiner(environment).getBalance())
        .isGreaterThanOrEqualTo(Wei.ZERO);
    assertThat(maybeBlock.get().getMiner(environment).getAddress()).isEqualTo(testAddress);
  }

  @Test
  void blobData() throws Exception {
    final long blobGasUsed = 0xb10b6a5;
    final long excessBlobGas = 0xce556a5;

    when(environment.getGraphQlContext()).thenReturn(graphQLContext);
    when(environment.getArgument("number")).thenReturn(1L);
    when(environment.getArgument("hash")).thenReturn(null);

    stubBlockByNumberPresent();
    doReturn(Optional.of(blobGasUsed)).when(header).getBlobGasUsed();
    doReturn(Optional.of(BlobGas.of(excessBlobGas))).when(header).getExcessBlobGas();

    final Optional<NormalBlockAdapter> maybeBlock = fetcher.get(environment);
    assertThat(maybeBlock).isPresent();
    assertThat(maybeBlock.get().getBlobGasUsed()).contains(blobGasUsed);
    assertThat(maybeBlock.get().getExcessBlobGas()).contains(excessBlobGas);
  }
}
