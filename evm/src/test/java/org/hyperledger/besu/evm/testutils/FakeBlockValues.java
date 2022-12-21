package org.hyperledger.besu.evm.testutils;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

import java.util.Optional;

public class FakeBlockValues implements BlockValues {
  final long number;
  final Optional<Wei> baseFee;

  public FakeBlockValues(final long number) {
    this(number, Optional.empty());
  }

  public FakeBlockValues(final Optional<Wei> baseFee) {
    this(1337, baseFee);
  }

  public FakeBlockValues(final long number, final Optional<Wei> baseFee) {
    this.number = number;
    this.baseFee = baseFee;
  }

  @Override
  public long getNumber() {
    return number;
  }

  @Override
  public Optional<Wei> getBaseFee() {
    return baseFee;
  }
}
