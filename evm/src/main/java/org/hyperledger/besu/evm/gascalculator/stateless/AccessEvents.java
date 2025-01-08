/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.gascalculator.stateless;

/**
 * Manages access events that are done to the AccessWitness. An access mode is passed to the witness
 * representing the type of access being done on the verkle trie leaf node: read, write to an
 * existing leaf (reset) or write to a non-existent leaf (set). The access mode is then evaluated
 * against structures that tracks previous access events, to avoid double charging. What results is
 * the **new** access event schedule that is then used for gas charging.
 */
public final class AccessEvents {

  private static final long WITNESS_BRANCH_COST = 1900;
  private static final long WITNESS_CHUNK_COST = 200;
  private static final long SUBTREE_EDIT_COST = 3000;
  private static final long CHUNK_EDIT_COST = 500;
  private static final long CHUNK_FILL_COST = 6200;

  /** Bit mask for NONE */
  public static final int NONE = 0;

  /** Bit mask for BRANCH READ */
  public static final int BRANCH_READ = 1;

  /** Bit mask for BRANCH WRITE */
  public static final int BRANCH_WRITE = 2;

  /** Bit mask for LEAF READ */
  public static final int LEAF_READ = 4;

  /** Bit mask for LEAF RESET */
  public static final int LEAF_RESET = 8;

  /** Bit mask for LEAF SET */
  public static final int LEAF_SET = 16;

  private AccessEvents() {}

  /**
   * Checks if a branch read event has occurred.
   *
   * @param accessEvents bit mask of all access events so far
   * @return true if bit mask contains a branch read event, false otherwise
   */
  public static boolean isBranchRead(final int accessEvents) {
    return (accessEvents & BRANCH_READ) != 0;
  }

  /**
   * Adds a branch read event to the bit mask of access events.
   *
   * @param accessEvents bit mask of all access events so far
   * @return bit mask with all access events plus a branch read
   */
  public static int branchRead(final int accessEvents) {
    return accessEvents | BRANCH_READ;
  }

  /**
   * Checks if a branch write event has occurred.
   *
   * @param accessEvents bit mask of all access events so far
   * @return true if bit mask contains a branch write event, false otherwise
   */
  public static boolean isBranchWrite(final int accessEvents) {
    return (accessEvents & BRANCH_WRITE) != 0;
  }

  /**
   * Adds a branch write event to the bit mask of access events.
   *
   * @param accessEvents bit mask of all access events so far
   * @return bit mask with all access events plus a branch write
   */
  public static int branchWrite(final int accessEvents) {
    return accessEvents | BRANCH_WRITE;
  }

  /**
   * Checks if a leaf read event has occurred.
   *
   * @param accessEvents bit mask of all access events so far
   * @return true if bit mask contains a leaf read event, false otherwise
   */
  public static boolean isLeafRead(final int accessEvents) {
    return (accessEvents & LEAF_READ) != 0;
  }

  /**
   * Adds a leaf read event to the bit mask of access events.
   *
   * @param accessEvents bit mask of all access events so far
   * @return bit mask with all access events plus a leaf read
   */
  public static int leafRead(final int accessEvents) {
    return accessEvents | LEAF_READ;
  }

  /**
   * Checks if a leaf reset event has occurred.
   *
   * @param accessEvents bit mask of all access events so far
   * @return true if bit mask contains a leaf reset event, false otherwise
   */
  public static boolean isLeafReset(final int accessEvents) {
    return (accessEvents & LEAF_RESET) != 0;
  }

  /**
   * Adds a leaf reset event to the bit mask of access events.
   *
   * @param accessEvents bit mask of all access events so far
   * @return bit mask with all access events plus a leaf reset
   */
  public static int leafReset(final int accessEvents) {
    return accessEvents | LEAF_RESET;
  }

  /**
   * Checks if a leaf set event has occurred.
   *
   * @param accessEvents bit mask of all access events so far
   * @return true if bit mask contains a leaf set event, false otherwise
   */
  public static boolean isLeafSet(final int accessEvents) {
    return (accessEvents & LEAF_SET) != 0;
  }

  /**
   * Adds a leaf set event to the bit mask of access events.
   *
   * @param accessEvents bit mask of all access events so far
   * @return bit mask with all access events plus a leaf set
   */
  public static int leafSet(final int accessEvents) {
    return accessEvents | LEAF_SET;
  }

  /**
   * Checks if a write event has occurred.
   *
   * @param accessEvents bit mask of all access events so far
   * @return true if bit mask contains any of the write events, false otherwise
   */
  public static boolean isWrite(final int accessEvents) {
    return isBranchWrite(accessEvents) || isLeafSet(accessEvents) || isLeafReset(accessEvents);
  }

  /**
   * The branch read event cost.
   *
   * @return cost of a branch read event.
   */
  public static long getBranchReadCost() {
    return WITNESS_BRANCH_COST;
  }

  /**
   * The leaf read event cost.
   *
   * @return cost of a leaf read event.
   */
  public static long getLeafReadCost() {
    return WITNESS_CHUNK_COST;
  }

  /**
   * The branch write event cost.
   *
   * @return cost of a branch write event.
   */
  public static long getBranchWriteCost() {
    return SUBTREE_EDIT_COST;
  }

  /**
   * The leaf reset event cost.
   *
   * @return cost of a leaf reset event.
   */
  public static long getLeafResetCost() {
    return CHUNK_EDIT_COST;
  }

  /**
   * The leaf set event cost.
   *
   * @return cost of a leaf set event.
   */
  public static long getLeafSetCost() {
    return CHUNK_FILL_COST;
  }
}
