package org.hyperledger.besu.ethereum.linea;

import static java.util.Objects.isNull;

import java.util.OptionalInt;

public class LineaParameters {
  public static final LineaParameters DEFAULT = new LineaParameters(null);

  private final OptionalInt transactionCalldataMaxSize;

  public LineaParameters(final Integer transactionCalldataMaxSize) {
    this.transactionCalldataMaxSize =
        isNull(transactionCalldataMaxSize)
            ? OptionalInt.empty()
            : OptionalInt.of(transactionCalldataMaxSize);
  }

  public OptionalInt maybeTransactionCalldataMaxSize() {
    return transactionCalldataMaxSize;
  }
}
