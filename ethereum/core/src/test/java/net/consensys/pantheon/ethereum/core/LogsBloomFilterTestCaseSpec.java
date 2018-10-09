package net.consensys.pantheon.ethereum.core;

import net.consensys.pantheon.ethereum.vm.LogMock;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A VM test case specification.
 *
 * <p>Note: this class will be auto-generated with the JSON test specification.
 */
@JsonIgnoreProperties("_info")
public class LogsBloomFilterTestCaseSpec {

  public LogsBloomFilter logsBloomFilter;

  public LogsBloomFilter finalBloom;

  public List<LogMock> logs;

  /** Public constructor. */
  @JsonCreator
  public LogsBloomFilterTestCaseSpec(
      @JsonProperty("logs") final List<LogMock> logs,
      @JsonProperty("bloom") final String finalBloom) {
    this.logs = logs;
    this.finalBloom = new LogsBloomFilter(BytesValue.fromHexString(finalBloom));
  }

  public List<LogMock> getLogs() {
    return logs;
  }

  /** @return - 2048-bit representation of each log entry, except data, of each transaction. */
  public LogsBloomFilter getLogsBloomFilter() {
    return logsBloomFilter;
  }

  public LogsBloomFilter getFinalBloom() {
    return finalBloom;
  }
}
