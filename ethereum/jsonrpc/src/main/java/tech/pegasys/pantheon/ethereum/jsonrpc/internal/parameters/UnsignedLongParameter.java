package tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.annotation.JsonCreator;

public class UnsignedLongParameter {

  private final long value;

  @JsonCreator
  public UnsignedLongParameter(final String value) {
    this.value = Long.decode(value);
    checkArgument(this.value >= 0);
  }

  public long getValue() {
    return value;
  }
}
