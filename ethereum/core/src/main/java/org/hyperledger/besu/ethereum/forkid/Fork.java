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
package org.hyperledger.besu.ethereum.forkid;

import java.util.Locale;
import java.util.Objects;

/**
 * Represents a fork point in the Ethereum protocol schedule.
 * Supports both block number-based and timestamp-based forks.
 */
public class Fork implements Comparable<Fork> {
  
  /** The type of fork activation */
  public enum Type {
    BLOCK_NUMBER,
    TIMESTAMP
  }
  
  private final long value;
  private final Type type;
  
  /**
   * Creates a block number-based fork.
   *
   * @param blockNumber the block number at which the fork activates
   * @return a Fork instance
   */
  public static Fork ofBlockNumber(final long blockNumber) {
    return new Fork(blockNumber, Type.BLOCK_NUMBER);
  }
  
  /**
   * Creates a timestamp-based fork.
   *
   * @param timestamp the timestamp at which the fork activates
   * @return a Fork instance
   */
  public static Fork ofTimestamp(final long timestamp) {
    return new Fork(timestamp, Type.TIMESTAMP);
  }
  
  private Fork(final long value, final Type type) {
    this.value = value;
    this.type = type;
  }
  
  public long getValue() {
    return value;
  }
  
  public Type getType() {
    return type;
  }
  
  public boolean isBlockNumber() {
    return type == Type.BLOCK_NUMBER;
  }
  
  public boolean isTimestamp() {
    return type == Type.TIMESTAMP;
  }
  
  @Override
  public int compareTo(final Fork other) {
    // First compare by value for chronological ordering
    int valueComparison = Long.compare(this.value, other.value);
    if (valueComparison != 0) {
      return valueComparison;
    }
    
    // If values are equal, block numbers come before timestamps
    // This ensures deterministic ordering when forks have the same value
    return this.type.compareTo(other.type);
  }
  
  @Override
  public boolean equals(final Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    
    final Fork fork = (Fork) obj;
    return value == fork.value && type == fork.type;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(value, type);
  }
  
  @Override
  public String toString() {
    return String.format("Fork{%s=%d}", type.name().toLowerCase(Locale.ROOT), value);
  }
}