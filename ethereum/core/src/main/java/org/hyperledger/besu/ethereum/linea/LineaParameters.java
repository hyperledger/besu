package org.hyperledger.besu.ethereum.linea;

import static java.util.Objects.isNull;

import java.util.OptionalInt;

public class LineaParameters {
  public static final LineaParameters DEFAULT = new LineaParameters(null, null);

  private final OptionalInt transactionCalldataMaxSize;
  private final OptionalInt blockCalldataMaxSize;

  public LineaParameters(
      final Integer transactionCalldataMaxSize, final Integer blockCalldataMaxSize) {
    this.transactionCalldataMaxSize =
        isNull(transactionCalldataMaxSize)
            ? OptionalInt.empty()
            : OptionalInt.of(transactionCalldataMaxSize);
    this.blockCalldataMaxSize =
        isNull(blockCalldataMaxSize) ? OptionalInt.empty() : OptionalInt.of(blockCalldataMaxSize);
  }

  public OptionalInt maybeTransactionCalldataMaxSize() {
    return transactionCalldataMaxSize;
  }

  public OptionalInt maybeBlockCalldataMaxSize() {
    return blockCalldataMaxSize;
  }
}
