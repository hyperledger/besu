package net.consensys.pantheon.ethereum.jsonrpc.internal.filter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.chain.BlockAddedEvent;
import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.TransactionPool;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.LogWithMetadata;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.Quantity;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FilterManagerLogFilterTest {

  private FilterManager filterManager;

  @Mock private Blockchain blockchain;
  @Mock private BlockchainQueries blockchainQueries;
  @Mock private TransactionPool transactionPool;

  @Before
  public void setupTest() {
    when(blockchainQueries.getBlockchain()).thenReturn(blockchain);
    this.filterManager =
        new FilterManager(blockchainQueries, transactionPool, new FilterIdGenerator());
  }

  @Test
  public void installUninstallNewLogFilter() {
    assertThat(filterManager.logFilterCount()).isEqualTo(0);

    final String filterId = filterManager.installLogFilter(latest(), latest(), logsQuery());
    assertThat(filterManager.logFilterCount()).isEqualTo(1);

    assertThat(filterManager.uninstallFilter(filterId)).isTrue();
    assertThat(filterManager.logFilterCount()).isEqualTo(0);

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

  private LogWithMetadata logWithMetadata() {
    return LogWithMetadata.create(
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
