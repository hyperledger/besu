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

public final class AccessEvents {

  private static final long WITNESS_BRANCH_COST = 1900;
  private static final long WITNESS_CHUNK_COST = 200;
  private static final long SUBTREE_EDIT_COST = 3000;
  private static final long CHUNK_EDIT_COST = 500;
  private static final long CHUNK_FILL_COST = 6200;

  public static final short NONE = 0;
  public static final short BRANCH_READ = 1;
  public static final short BRANCH_WRITE = 2;
  public static final short LEAF_READ = 4;
  public static final short LEAF_RESET = 8;
  public static final short LEAF_SET = 16;

  private AccessEvents() {}

  public static boolean isBranchRead(final short accessEvents) {
    return (accessEvents & BRANCH_READ) != 0;
  }

  public static boolean isBranchWrite(final short accessEvents) {
    return (accessEvents & BRANCH_WRITE) != 0;
  }

  public static boolean isLeafRead(final short accessEvents) {
    return (accessEvents & LEAF_READ) != 0;
  }

  public static boolean isLeafReset(final short accessEvents) {
    return (accessEvents & LEAF_RESET) != 0;
  }

  public static boolean isLeafSet(final short accessEvents) {
    return (accessEvents & LEAF_SET) != 0;
  }

  public static long getBranchReadCost() {
    return WITNESS_BRANCH_COST;
  }

  public static long getLeafReadCost() {
    return WITNESS_CHUNK_COST;
  }

  public static long getBranchWriteCost() {
    return SUBTREE_EDIT_COST;
  }

  public static long getLeafResetCost() {
    return CHUNK_EDIT_COST;
  }

  public static long getLeafSetCost() {
    return CHUNK_FILL_COST;
  }
}
