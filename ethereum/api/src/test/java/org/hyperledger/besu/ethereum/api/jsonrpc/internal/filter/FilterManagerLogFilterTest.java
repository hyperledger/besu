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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.LogsQuery;
import org.hyperledger.besu.ethereum.api.query.PrivacyQueries;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.LogWithMetadata;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionEvent;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FilterManagerLogFilterTest {

  private static final String PRIVACY_GROUP_ID = "Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs=";
  private static final String ENCLAVE_PUBLIC_KEY = "iOCzoGo5kwtZU0J41Z9xnGXHN6ZNukIa9MspvHtu3Jk=";

  private FilterManager filterManager;

  @Mock private Blockchain blockchain;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private PrivacyQueries privacyQueries;
  @Mock private TransactionPool transactionPool;
  @Spy private final FilterRepository filterRepository = new FilterRepository();

  @Before
  public void setupTest() {
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    this.filterManager =
        new FilterManagerBuilder()
            .blockchainQueries(blockchainQueries)
            .transactionPool(transactionPool)
            .privacyQueries(privacyQueries)
            .filterRepository(filterRepository)
            .build();
  }

  @Test
  public void installUninstallNewLogFilter() {
    final String filterId = filterManager.installLogFilter(latest(), latest(), logsQuery());
    assertThat(filterRepository.exists(filterId)).isTrue();

    assertThat(filterManager.uninstallFilter(filterId)).isTrue();
    assertThat(filterRepository.exists(filterId)).isFalse();

    assertThat(filterManager.blockChanges(filterId)).isNull();
  }

  @Test
  public void shouldCheckMatchingLogsWhenRecordedNewBlockEventForPrivateFiltersOnly() {
    filterManager.installPrivateLogFilter(
        PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY, latest(), latest(), logsQuery());
    filterManager.installLogFilter(latest(), latest(), logsQuery());
    final Hash blockAddedHash = recordBlockEvents(1).get(0).getBlock().getHash();

    verify(blockchainQueries, never()).matchingLogs(eq(100L), eq(100L), eq(logsQuery()), any());
    verify(privacyQueries).matchingLogs(eq(PRIVACY_GROUP_ID), eq(blockAddedHash), eq(logsQuery()));
  }

  @Test
  public void shouldUseBlockHashWhenCheckingLogsForChangesForPrivateFiltersOnly() {
    filterManager.installPrivateLogFilter(
        PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY, blockNum(1L), blockNum(10L), logsQuery());
    filterManager.installLogFilter(blockNum(1L), blockNum(10L), logsQuery());

    final Hash blockAddedHash = recordBlockEvents(1).get(0).getBlock().getHash();

    verify(blockchainQueries, never()).matchingLogs(any(), any(), any());
    verify(privacyQueries).matchingLogs(eq(PRIVACY_GROUP_ID), eq(blockAddedHash), eq(logsQuery()));
  }

  @Test
  public void shouldReturnLogWhenLogFilterMatches() {

    final String filterId = filterManager.installLogFilter(latest(), latest(), logsQuery());
    final List<LogWithMetadata> expectedLogs = recordBlockEvents(1).get(0).getLogsWithMetadata();

    final List<LogWithMetadata> retrievedLogs = filterManager.logsChanges(filterId);

    assertThat(retrievedLogs).usingRecursiveComparison().isEqualTo(expectedLogs);
  }

  @Test
  public void shouldNotReturnLogOnNewBlockIfToBlockIsInPast() {
    final String filterId =
        filterManager.installLogFilter(
            new BlockParameter("0"), new BlockParameter("1"), logsQuery());
    recordBlockEvents(1).get(0);

    final List<LogWithMetadata> retrievedLogs = filterManager.logsChanges(filterId);

    assertThat(retrievedLogs).isEqualTo(emptyList());
  }

  @Test
  public void shouldNotQueryOnNewBlock() {
    filterManager.installLogFilter(latest(), latest(), logsQuery());
    filterManager.installLogFilter(latest(), latest(), logsQuery());
    filterManager.installLogFilter(latest(), latest(), logsQuery());
    recordBlockEvents(1);

    verify(blockchainQueries, never()).matchingLogs(anyLong(), anyLong(), any(), any());
  }

  @Test
  public void shouldReturnNullWhenForAbsentLogFilter() {
    final List<LogWithMetadata> logs = filterManager.logsChanges("NOT THERE");

    assertThat(logs).isNull();
    verify(blockchainQueries, never()).matchingLogs(anyLong(), anyLong(), any(), any());
  }

  @Test
  public void shouldClearLogsAfterGettingLogChanges() {
    final String filterId = filterManager.installLogFilter(latest(), latest(), logsQuery());
    recordBlockEvents(2);

    assertThat(filterManager.logsChanges(filterId).size()).isEqualTo(8);
    assertThat(filterManager.logsChanges(filterId).size()).isEqualTo(0);
  }

  private List<BlockAddedEvent> recordBlockEvents(final int numEvents) {
    final BlockDataGenerator gen = new BlockDataGenerator();
    final List<BlockAddedEvent> blockAddedEvents =
        Stream.generate(
                () -> {
                  final Block block =
                      gen.block(new BlockDataGenerator.BlockOptions().setBlockNumber(3));
                  return BlockAddedEvent.createForHeadAdvancement(
                      block,
                      LogWithMetadata.generate(block, gen.receipts(block), false),
                      emptyList());
                })
            .limit(numEvents)
            .collect(toUnmodifiableList());
    blockAddedEvents.forEach(event -> filterManager.recordBlockEvent(event));
    return blockAddedEvents;
  }

  @Test
  public void getLogsForAbsentFilterReturnsNull() {
    assertThat(filterManager.logs("NOTTHERE")).isNull();
  }

  @Test
  public void getLogsForExistingFilterReturnsResults() {
    final LogWithMetadata log = logWithMetadata();
    when(blockchainQueries.headBlockNumber()).thenReturn(100L);
    when(blockchainQueries.matchingLogs(eq(100L), eq(100L), eq(logsQuery()), any()))
        .thenReturn(singletonList(log));

    final String filterId = filterManager.installLogFilter(latest(), latest(), logsQuery());
    final List<LogWithMetadata> retrievedLogs = filterManager.logs(filterId);

    assertThat(retrievedLogs).usingRecursiveComparison().isEqualTo(singletonList(log));
  }

  @Test
  public void getLogsChangesShouldResetFilterExpireDate() {
    final LogFilter filter = spy(new LogFilter("foo", latest(), latest(), logsQuery()));
    doReturn(Optional.of(filter)).when(filterRepository).getFilter(eq("foo"), eq(LogFilter.class));

    filterManager.logsChanges("foo");

    verify(filter).resetExpireTime();
  }

  @Test
  public void getLogsShouldResetFilterExpireDate() {
    final LogFilter filter = spy(new LogFilter("foo", latest(), latest(), logsQuery()));
    doReturn(Optional.of(filter)).when(filterRepository).getFilter(eq("foo"), eq(LogFilter.class));

    filterManager.logs("foo");

    verify(filter).resetExpireTime();
  }

  @Test
  public void installAndUninstallPrivateFilter() {
    final String filterId =
        filterManager.installPrivateLogFilter(
            PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY, latest(), latest(), logsQuery());

    assertThat(filterRepository.getFilter(filterId, PrivateLogFilter.class)).isPresent();

    assertThat(filterManager.uninstallFilter(filterId)).isTrue();

    assertThat(filterRepository.getFilter(filterId, PrivateLogFilter.class)).isEmpty();
  }

  @Test
  public void privateLogFilterShouldQueryPrivacyQueriesObject() {
    final LogWithMetadata logWithMetadata = logWithMetadata();
    final LogsQuery logsQuery = logsQuery();
    when(privacyQueries.matchingLogs(eq(PRIVACY_GROUP_ID), any(), eq(logsQuery)))
        .thenReturn(singletonList(logWithMetadata));

    final String filterId =
        filterManager.installPrivateLogFilter(
            PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY, latest(), latest(), logsQuery);
    final Hash blockAddedHash = recordBlockEvents(1).get(0).getBlock().getHash();

    verify(privacyQueries).matchingLogs(eq(PRIVACY_GROUP_ID), eq(blockAddedHash), eq(logsQuery));
    assertThat(filterManager.logsChanges(filterId).get(0)).isEqualTo(logWithMetadata);
  }

  @Test
  public void getLogsForPrivateFilterShouldQueryPrivacyQueriesObject() {
    final LogWithMetadata logWithMetadata = logWithMetadata();
    when(privacyQueries.matchingLogs(eq(PRIVACY_GROUP_ID), anyLong(), anyLong(), any()))
        .thenReturn(Lists.newArrayList(logWithMetadata));

    final String privateLogFilterId =
        filterManager.installPrivateLogFilter(
            PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY, latest(), latest(), logsQuery());

    final List<LogWithMetadata> logs = filterManager.logs(privateLogFilterId);

    verify(blockchainQueries, times(2)).headBlockNumber();
    verify(blockchainQueries, never()).matchingLogs(anyLong(), anyLong(), any(), any());

    verify(privacyQueries).matchingLogs(eq(PRIVACY_GROUP_ID), anyLong(), anyLong(), any());
    assertThat(logs.get(0)).isEqualTo(logWithMetadata);
  }

  @Test
  public void removalEvent_uninstallsFilter() {
    final LogWithMetadata logWithMetadata = logWithMetadata();
    when(privacyQueries.matchingLogs(eq(PRIVACY_GROUP_ID), anyLong(), anyLong(), any()))
        .thenReturn(Lists.newArrayList(logWithMetadata));

    final String privateLogFilterId =
        filterManager.installPrivateLogFilter(
            PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY, latest(), latest(), logsQuery());

    final List<LogWithMetadata> logs = filterManager.logs(privateLogFilterId);

    verify(blockchainQueries, times(2)).headBlockNumber();
    verify(blockchainQueries, never()).matchingLogs(anyLong(), anyLong(), any(), any());

    verify(privacyQueries).matchingLogs(eq(PRIVACY_GROUP_ID), anyLong(), anyLong(), any());
    assertThat(logs.get(0)).isEqualTo(logWithMetadata);

    // signal removal of user from group
    privateTransactionEvent(PRIVACY_GROUP_ID, ENCLAVE_PUBLIC_KEY);

    assertThat(filterManager.logs(privateLogFilterId)).isNull();
    assertThat(filterRepository.getFilter(privateLogFilterId, PrivateLogFilter.class)).isEmpty();
  }

  private void privateTransactionEvent(final String privacyGroupId, final String privacyUserId) {
    PrivateTransactionEvent event = new PrivateTransactionEvent(privacyGroupId, privacyUserId);
    filterManager.processRemovalEvent(event);
  }

  private LogWithMetadata logWithMetadata() {
    return new LogWithMetadata(
        0,
        100L,
        Hash.ZERO,
        Hash.ZERO,
        0,
        Address.fromHexString("0x0"),
        Bytes.EMPTY,
        Lists.newArrayList(),
        false);
  }

  private LogsQuery logsQuery() {
    return new LogsQuery(emptyList(), emptyList()); // matches everything
  }

  private BlockParameter latest() {
    return new BlockParameter("latest");
  }

  private BlockParameter blockNum(final long blockNum) {
    return new BlockParameter(Quantity.create(blockNum));
  }
}
