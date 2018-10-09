package net.consensys.pantheon.ethereum.jsonrpc.internal.parameters;

import net.consensys.pantheon.ethereum.core.BlockHeader;

import java.util.Objects;
import java.util.OptionalLong;

import com.fasterxml.jackson.annotation.JsonCreator;

// Represents a block parameter that can be a special value ("pending", "earliest", "latest") or
// a number formatted as a hex string.
// See: https://github.com/ethereum/wiki/wiki/JSON-RPC#the-default-block-parameter
public class BlockParameter {

  private final BlockParameterType type;
  private final OptionalLong number;

  @JsonCreator
  public BlockParameter(final String value) {
    final String normalizedValue = value.toLowerCase();

    if (Objects.equals(normalizedValue, "earliest")) {
      type = BlockParameterType.EARLIEST;
      number = OptionalLong.of(BlockHeader.GENESIS_BLOCK_NUMBER);
    } else if (Objects.equals(normalizedValue, "latest")) {
      type = BlockParameterType.LATEST;
      number = OptionalLong.empty();
    } else if (Objects.equals(normalizedValue, "pending")) {
      type = BlockParameterType.PENDING;
      number = OptionalLong.empty();
    } else {
      type = BlockParameterType.NUMERIC;
      number = OptionalLong.of(Long.decode(value));
    }
  }

  public OptionalLong getNumber() {
    return number;
  }

  public boolean isPending() {
    return this.type == BlockParameterType.PENDING;
  }

  public boolean isLatest() {
    return this.type == BlockParameterType.LATEST;
  }

  public boolean isEarliest() {
    return this.type == BlockParameterType.EARLIEST;
  }

  public boolean isNumeric() {
    return this.type == BlockParameterType.NUMERIC;
  }

  private enum BlockParameterType {
    EARLIEST,
    LATEST,
    PENDING,
    NUMERIC;
  }
}
