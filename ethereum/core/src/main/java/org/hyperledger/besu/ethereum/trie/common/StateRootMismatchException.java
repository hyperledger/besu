/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.trie.common;

import org.hyperledger.besu.datatypes.Hash;

public class StateRootMismatchException extends RuntimeException {

  private final Hash actualRoot;
  private final Hash expectedRoot;

  public StateRootMismatchException(final Hash expectedRoot, final Hash actualRoot) {
    this.expectedRoot = expectedRoot;
    this.actualRoot = actualRoot;
  }

  @Override
  public String getMessage() {
    return "World State Root does not match expected value, header "
        + expectedRoot.toHexString()
        + " calculated "
        + actualRoot.toHexString();
  }

  public Hash getActualRoot() {
    return actualRoot;
  }

  public Hash getExpectedRoot() {
    return expectedRoot;
  }
}
