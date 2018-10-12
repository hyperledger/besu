package net.consensys.pantheon.ethereum.jsonrpc.internal.results;

import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.LogWithMetadata;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

/** The result set from querying the logs from one or more blocks. */
public class LogsResult {

  private final List<LogResult> results;

  public LogsResult(final List<LogWithMetadata> logs) {
    results = new ArrayList<>(logs.size());

    for (final LogWithMetadata log : logs) {
      results.add(new LogResult(log));
    }
  }

  @JsonValue
  public List<LogResult> getResults() {
    return results;
  }
}
