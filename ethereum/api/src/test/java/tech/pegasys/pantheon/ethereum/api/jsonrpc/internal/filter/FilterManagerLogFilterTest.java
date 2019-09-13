/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.filter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.api.LogWithMetadata;
import tech.pegasys.pantheon.ethereum.api.LogsQuery;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters.BlockParameter;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.Quantity;
import tech.pegasys.pantheon.ethereum.chain.BlockAddedEvent;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockDataGenerator;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.transactions.TransactionPool;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FilterManagerLogFilterTest {

  private FilterManager filterManager;

  @Mock private Blockchain blockchain;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private TransactionPool transactionPool;
  @Spy private final FilterRepository filterRepository = new FilterRepository();

  @Before
  public void setupTest() {
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    this.filterManager =
        new FilterManager(
            blockchainQueries, transactionPool, new FilterIdGenerator(), filterRepository);
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
  public void shouldCheckMatchingLogsWhenRecordedNewBlockEvent() {
    when(blockchainQueries.headBlockNumber()).thenReturn(100L);

    filterManager.installLogFilter(latest(), latest(), logsQuery());
    recordNewBlockEvent();

    verify(blockchainQueries).matchingLogs(eq(100L), eq(100L), refEq(logsQuery()));
  }

  @Test
  public void shouldUseHeadBlockAsFromBlockNumberWhenCheckingLogsForChanges() {
    when(blockchainQueries.headBlockNumber()).thenReturn(3L);

    filterManager.installLogFilter(blockNum(1L), blockNum(10L), logsQuery());
    recordNewBlockEvent();

    verify(blockchainQueries).matchingLogs(eq(3L), eq(10L), refEq(logsQuery()));
  }

  @Test
  public void shouldReturnLogWhenLogFilterMatches() {
    final LogWithMetadata log = logWithMetadata();
    when(blockchainQueries.headBlockNumber()).thenReturn(100L);
    when(blockchainQueries.matchingLogs(eq(100L), eq(100L), refEq(logsQuery())))
        .thenReturn(Lists.newArrayList(log));

    final String filterId = filterManager.installLogFilter(latest(), latest(), logsQuery());
    recordNewBlockEvent();

    final List<LogWithMetadata> retrievedLogs = filterManager.logsChanges(filterId);

    assertThat(retrievedLogs).isEqualToComparingFieldByFieldRecursively(Lists.newArrayList(log));
  }

  @Test
  public void shouldCheckLogsForEveryLogFilter() {
    filterManager.installLogFilter(latest(), latest(), logsQuery());
    filterManager.installLogFilter(latest(), latest(), logsQuery());
    filterManager.installLogFilter(latest(), latest(), logsQuery());
    recordNewBlockEvent();

    verify(blockchainQueries, times(3)).matchingLogs(anyLong(), anyLong(), any());
  }

  @Test
  public void shouldReturnNullWhenForAbsentLogFilter() {
    final List<LogWithMetadata> logs = filterManager.logsChanges("NOT THERE");

    assertThat(logs).isNull();
    verify(blockchainQueries, times(0)).matchingLogs(anyLong(), anyLong(), any());
  }

  @Test
  public void shouldClearLogsAfterGettingLogChanges() {
    when(blockchainQueries.matchingLogs(anyLong(), anyLong(), any()))
        .thenReturn(Lists.newArrayList(logWithMetadata()));

    final String filterId = filterManager.installLogFilter(latest(), latest(), logsQuery());
    recordNewBlockEvent();
    recordNewBlockEvent();

    assertThat(filterManager.logsChanges(filterId).size()).isEqualTo(2);
    assertThat(filterManager.logsChanges(filterId).size()).isEqualTo(0);
  }

  private void recordNewBlockEvent() {
    filterManager.recordBlockEvent(
        BlockAddedEvent.createForHeadAdvancement(new BlockDataGenerator().block()),
        blockchainQueries.getBlockchain());
  }

  @Test
  public void getLogsForAbsentFilterReturnsNull() {
    assertThat(filterManager.logs("NOTTHERE")).isNull();
  }

  @Test
  public void getLogsForExistingFilterReturnsResults() {
    final LogWithMetadata log = logWithMetadata();
    when(blockchainQueries.headBlockNumber()).thenReturn(100L);
    when(blockchainQueries.matchingLogs(eq(100L), eq(100L), refEq(logsQuery())))
        .thenReturn(Lists.newArrayList(log));

    final String filterId = filterManager.installLogFilter(latest(), latest(), logsQuery());
    final List<LogWithMetadata> retrievedLogs = filterManager.logs(filterId);

    assertThat(retrievedLogs).isEqualToComparingFieldByFieldRecursively(Lists.newArrayList(log));
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

  private LogWithMetadata logWithMetadata() {
    return new LogWithMetadata(
        0,
        100L,
        Hash.ZERO,
        Hash.ZERO,
        0,
        Address.fromHexString("0x0"),
        BytesValue.EMPTY,
        Lists.newArrayList(),
        false);
  }

  private LogsQuery logsQuery() {
    return new LogsQuery.Builder().build();
  }

  private BlockParameter latest() {
    return new BlockParameter("latest");
  }

  private BlockParameter blockNum(final long blockNum) {
    return new BlockParameter(Quantity.create(blockNum));
  }
}
