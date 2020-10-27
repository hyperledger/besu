package org.hyperledger.besu.metrics;

/** Enumeration of metrics protocols supported by Besu. */
public enum MetricsProtocol {
  PROMETHEUS,
  OPENTELEMETRY,
  NOOP;

  public static MetricsProtocol fromString(final String str) {
    for (final MetricsProtocol mode : MetricsProtocol.values()) {
      if (mode.name().equalsIgnoreCase(str)) {
        return mode;
      }
    }
    return null;
  }
}
