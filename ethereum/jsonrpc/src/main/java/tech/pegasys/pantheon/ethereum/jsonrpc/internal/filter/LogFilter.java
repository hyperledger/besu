package tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.BlockParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.LogWithMetadata;

import java.util.ArrayList;
import java.util.List;

class LogFilter extends Filter {

  private final BlockParameter fromBlock;
  private final BlockParameter toBlock;
  private final LogsQuery logsQuery;

  private final List<LogWithMetadata> logs = new ArrayList<>();

  LogFilter(
      final String id,
      final BlockParameter fromBlock,
      final BlockParameter toBlock,
      final LogsQuery logsQuery) {
    super(id);
    this.fromBlock = fromBlock;
    this.toBlock = toBlock;
    this.logsQuery = logsQuery;
  }

  public BlockParameter getFromBlock() {
    return fromBlock;
  }

  public BlockParameter getToBlock() {
    return toBlock;
  }

  public LogsQuery getLogsQuery() {
    return logsQuery;
  }

  void addLog(final List<LogWithMetadata> logs) {
    this.logs.addAll(logs);
  }

  List<LogWithMetadata> logs() {
    return logs;
  }

  void clearLogs() {
    logs.clear();
  }
}
