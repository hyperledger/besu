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
package org.hyperledger.besu.evm.code;

/**
 * A work list, allowing a DAG to be evaluated while detecting disconnected sections.
 *
 * <p>When an item is marked if it has not been marked it is added to the work list. `take()`
 * returns the fist item that has not yet been returned from a take, or `-1` if no items are
 * available. Items are added by calling `put(int)`, which is idempotent. Items can be put several
 * times but will only be taken once.
 *
 * <p>`isComplete()` checks if all items have been taken. `getFirstUnmarkedItem()` is used when
 * reporting errors to identify an unconnected item.
 */
class WorkList {
  boolean[] marked;
  int[] items;
  int nextIndex;
  int listEnd;

  /**
   * Create a work list of the appropriate size. The list is empty.
   *
   * @param size number of possible items
   */
  WorkList(final int size) {
    marked = new boolean[size];
    items = new int[size];
    nextIndex = 0;
    listEnd = -1;
  }

  /**
   * Take the next item, if available
   *
   * @return the item number, or -1 if no items are available.
   */
  int take() {
    if (nextIndex > listEnd) {
      return -1;
    }
    int result = items[nextIndex];
    nextIndex++;
    return result;
  }

  /**
   * Have all items been taken?
   *
   * @return true if all items were marked and then taken
   */
  boolean isComplete() {
    return nextIndex >= items.length;
  }

  /**
   * Put an item in the work list. This is idempotent, an item will only be added on the first call.
   *
   * @param item the item to add to the list.
   */
  void put(final int item) {
    if (!marked[item]) {
      listEnd++;
      items[listEnd] = item;
      marked[item] = true;
    }
  }

  /**
   * Walks the taken list and returns the first unmarked item
   *
   * @return the first unmarked item, or -1 if all items are marked.
   */
  int getFirstUnmarkedItem() {
    for (int i = 0; i < marked.length; i++) {
      if (!marked[i]) {
        return i;
      }
    }
    return -1;
  }
}
