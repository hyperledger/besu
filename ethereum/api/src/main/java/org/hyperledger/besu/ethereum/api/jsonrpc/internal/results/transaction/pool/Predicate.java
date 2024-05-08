/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.transaction.pool;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * This class describes the behavior of predicates that can be used to filter pending transactions
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public enum Predicate {
  /** Eq predicate. */
  EQ((left, right) -> left.compareTo(right) == 0),
  /** Lt predicate. */
  LT((left, right) -> left.compareTo(right) < 0),
  /** Gt predicate. */
  GT((left, right) -> left.compareTo(right) > 0),
  /** Action predicate. */
  ACTION((left, right) -> false);

  private final Operator operator;

  Predicate(final Operator predicate) {
    this.operator = predicate;
  }

  /**
   * Gets operator.
   *
   * @return the operator
   */
  public Operator getOperator() {
    return operator;
  }

  /** The interface Operator. */
  @FunctionalInterface
  public interface Operator {
    /**
     * Apply boolean.
     *
     * @param left the left
     * @param right the right
     * @return the boolean
     */
    boolean apply(final Comparable left, final Comparable right);
  }

  /**
   * From value optional.
   *
   * @param value the value
   * @return the optional
   */
  public static Optional<Predicate> fromValue(final String value) {
    return Stream.of(values()).filter(predicate -> predicate.name().equals(value)).findFirst();
  }
}
