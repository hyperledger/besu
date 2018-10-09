package net.consensys.pantheon.ethereum.jsonrpc.internal.parameters;

import net.consensys.pantheon.util.uint.UInt256;

import com.fasterxml.jackson.annotation.JsonCreator;

public class UInt256Parameter {

  private final UInt256 value;

  @JsonCreator
  public UInt256Parameter(final String value) {
    this.value = UInt256.fromHexString(value);
  }

  public UInt256 getValue() {
    return value;
  }
}
