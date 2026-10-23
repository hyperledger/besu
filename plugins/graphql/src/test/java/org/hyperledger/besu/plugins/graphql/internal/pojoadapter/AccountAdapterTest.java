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
package org.hyperledger.besu.plugins.graphql.internal.pojoadapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.WorldStateService;
import org.hyperledger.besu.plugins.graphql.GraphQLContextType;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Where an account's code and storage are read from.
 *
 * <p>Besu's core GraphQL read the code and storage of an account that is not a {@code
 * BonsaiAccount}, so of every account on a Forest world state, from the account itself: the state
 * of the block it was read at. The plugin may not use an account after its world state is closed,
 * so it reads them again from the world state of that block. For a {@code BonsaiAccount}, core read
 * them from the state of the block number given to the adapter, or else of the chain head, and so
 * does the plugin.
 */
@ExtendWith(MockitoExtension.class)
class AccountAdapterTest {

  private static final Address ADDRESS =
      Address.fromHexString("0x6295ee1b4f6dd65047762f924ecd367c17eabf8f");
  private static final Hash ORIGIN_HASH = Hash.hash(Bytes.of(1));
  private static final Hash HEAD_HASH = Hash.hash(Bytes.of(2));
  private static final Hash BLOCK_3_HASH = Hash.hash(Bytes.of(3));
  private static final long HEAD_NUMBER = 10L;
  private static final Bytes32 SLOT = Bytes32.fromHexStringLenient("0x04");

  private static final Bytes ORIGIN_CODE = Bytes.fromHexString("0x6001");
  private static final Bytes HEAD_CODE = Bytes.fromHexString("0x6002");
  private static final Bytes BLOCK_3_CODE = Bytes.fromHexString("0x6003");
  private static final UInt256 ORIGIN_VALUE = UInt256.valueOf(1);
  private static final UInt256 HEAD_VALUE = UInt256.valueOf(2);
  private static final UInt256 BLOCK_3_VALUE = UInt256.valueOf(3);

  @Mock private DataFetchingEnvironment environment;
  @Mock private BlockchainService blockchainService;
  @Mock private WorldStateService worldStateService;

  /** The account as read from the world state of the origin block, closed since. */
  @Mock private Account readAccount;

  @BeforeEach
  void setUp() {
    // lenient, as not every test reads an account
    lenient().when(readAccount.getAddress()).thenReturn(ADDRESS);
    lenient().when(readAccount.getBalance()).thenReturn(Wei.of(7));
    lenient().when(readAccount.getNonce()).thenReturn(5L);
  }

  private void withWorldState(final boolean bonsai) {
    when(environment.getGraphQlContext())
        .thenReturn(
            GraphQLContext.of(
                Map.of(
                    GraphQLContextType.BLOCKCHAIN_SERVICE,
                    blockchainService,
                    GraphQLContextType.WORLD_STATE_SERVICE,
                    worldStateService,
                    GraphQLContextType.BONSAI_WORLD_STATE,
                    bonsai)));
  }

  /** The world state after the block with the given hash holds the given code and value. */
  @SuppressWarnings("unchecked")
  private void stateAt(final Hash blockHash, final Bytes code, final UInt256 value) {
    final Account account = mock(Account.class);
    // lenient, as a test may read only the code or only the storage
    lenient().when(account.getCode()).thenReturn(code);
    lenient().when(account.getStorageValue(UInt256.fromBytes(SLOT))).thenReturn(value);
    final WorldView worldView = mock(WorldView.class);
    when(worldView.get(ADDRESS)).thenReturn(account);
    when(worldStateService.mapWorldView(eq(blockHash), any()))
        .thenAnswer(
            invocation ->
                ((Function<WorldView, Optional<?>>) invocation.getArgument(1)).apply(worldView));
  }

  private void canonicalHeader(final long number, final Hash hash) {
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getBlockHash()).thenReturn(hash);
    when(blockchainService.getBlockHeaderByNumber(number)).thenReturn(Optional.of(header));
  }

  private void chainHead() {
    final BlockHeader head = mock(BlockHeader.class);
    when(head.getNumber()).thenReturn(HEAD_NUMBER);
    when(blockchainService.getChainHeadHeader()).thenReturn(head);
    canonicalHeader(HEAD_NUMBER, HEAD_HASH);
  }

  @Test
  void forestReadsCodeAtTheOriginBlock() {
    withWorldState(false);
    stateAt(ORIGIN_HASH, ORIGIN_CODE, ORIGIN_VALUE);
    final AccountAdapter adapter = new AccountAdapter(readAccount, ORIGIN_HASH);

    assertThat(adapter.getCode(environment)).isEqualTo(ORIGIN_CODE);

    verify(worldStateService, never()).mapWorldView(eq(HEAD_HASH), any());
    verifyNoInteractions(blockchainService);
    verify(readAccount, never()).getCode();
  }

  @Test
  void forestReadsStorageAtTheOriginBlock() {
    withWorldState(false);
    when(environment.getArgument("slot")).thenReturn(SLOT);
    stateAt(ORIGIN_HASH, ORIGIN_CODE, ORIGIN_VALUE);
    final AccountAdapter adapter = new AccountAdapter(readAccount, ORIGIN_HASH);

    assertThat(adapter.getStorage(environment)).isEqualTo(ORIGIN_VALUE);

    verify(worldStateService, never()).mapWorldView(eq(HEAD_HASH), any());
    verifyNoInteractions(blockchainService);
    verify(readAccount, never()).getStorageValue(any());
  }

  @Test
  void forestIgnoresTheBlockNumberGivenToTheAdapter() {
    // On Forest, core read the code from the account, whatever block number the adapter was given.
    withWorldState(false);
    when(environment.getArgument("slot")).thenReturn(SLOT);
    stateAt(ORIGIN_HASH, ORIGIN_CODE, ORIGIN_VALUE);
    final AccountAdapter adapter =
        new AccountAdapter(readAccount, ORIGIN_HASH, Optional.of(HEAD_NUMBER));

    assertThat(adapter.getCode(environment)).isEqualTo(ORIGIN_CODE);
    assertThat(adapter.getStorage(environment)).isEqualTo(ORIGIN_VALUE);
    verifyNoInteractions(blockchainService);
  }

  @Test
  void forestKeepsTheBalanceAndNonceReadAtTheOriginBlock() {
    final AccountAdapter adapter = new AccountAdapter(readAccount, ORIGIN_HASH);

    assertThat(adapter.getAddress()).isEqualTo(ADDRESS);
    assertThat(adapter.getBalance()).isEqualTo(Wei.of(7));
    assertThat(adapter.getTransactionCount()).isEqualTo(5L);
  }

  @Test
  void forestFailsAsBeforeWhenThereIsNoAccount() {
    // core called Optional.get() on the missing account, failing the field with this message
    final AccountAdapter adapter = new AccountAdapter(ADDRESS, null, ORIGIN_HASH);

    assertThatThrownBy(() -> adapter.getCode(environment)).hasMessage("No value present");
    verifyNoInteractions(worldStateService);
  }

  @Test
  void bonsaiReadsCodeAndStorageAtTheChainHead() {
    withWorldState(true);
    when(environment.getArgument("slot")).thenReturn(SLOT);
    chainHead();
    stateAt(HEAD_HASH, HEAD_CODE, HEAD_VALUE);
    final AccountAdapter adapter = new AccountAdapter(readAccount, ORIGIN_HASH);

    assertThat(adapter.getCode(environment)).isEqualTo(HEAD_CODE);
    assertThat(adapter.getStorage(environment)).isEqualTo(HEAD_VALUE);
    verify(worldStateService, never()).mapWorldView(eq(ORIGIN_HASH), any());
  }

  @Test
  void bonsaiReadsCodeAndStorageAtTheBlockNumberGivenToTheAdapter() {
    withWorldState(true);
    when(environment.getArgument("slot")).thenReturn(SLOT);
    canonicalHeader(3L, BLOCK_3_HASH);
    stateAt(BLOCK_3_HASH, BLOCK_3_CODE, BLOCK_3_VALUE);
    final AccountAdapter adapter = new AccountAdapter(readAccount, ORIGIN_HASH, Optional.of(3L));

    assertThat(adapter.getCode(environment)).isEqualTo(BLOCK_3_CODE);
    assertThat(adapter.getStorage(environment)).isEqualTo(BLOCK_3_VALUE);
    verify(worldStateService, never()).mapWorldView(eq(ORIGIN_HASH), any());
  }
}
