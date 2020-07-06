package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * This class describes the behavior of predicates that can be used to filter pending transactions
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public enum Predicate {
  EQ((left, right) -> left.compareTo(right) == 0),
  LT((left, right) -> left.compareTo(right) < 0),
  GT((left, right) -> left.compareTo(right) > 0),
  ACTION((left, right) -> false);

  private final Operator operator;

  Predicate(final Operator predicate) {
    this.operator = predicate;
  }

  public Operator getOperator() {
    return operator;
  }

  @FunctionalInterface
  public interface Operator {
    boolean apply(final Comparable left, final Comparable right);
  }

  public static Optional<Predicate> fromValue(final String value) {
    return Stream.of(values()).filter(predicate -> predicate.name().equals(value)).findFirst();
  }
}
