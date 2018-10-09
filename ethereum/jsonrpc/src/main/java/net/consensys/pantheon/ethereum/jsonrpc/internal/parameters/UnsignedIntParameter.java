package net.consensys.pantheon.ethereum.jsonrpc.internal.parameters;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.annotation.JsonCreator;

public class UnsignedIntParameter {

  private final int value;

  @JsonCreator
  public UnsignedIntParameter(final String value) {
    this.value = Integer.decode(value);
    checkArgument(this.value >= 0);
  }

  public int getValue() {
    return value;
  }
}
