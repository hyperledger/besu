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
package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

public class BackwardSyncException extends RuntimeException {

  private final boolean restartable;

  public BackwardSyncException(final String message) {
    this(message, false);
  }

  public BackwardSyncException(final Throwable error) {
    this(error, false);
  }

  public BackwardSyncException(final String message, final boolean restartable) {
    super(message);
    this.restartable = restartable;
  }

  public BackwardSyncException(final Throwable error, final boolean restartable) {
    super(error);
    this.restartable = restartable;
  }

  public boolean shouldRestart() {
    return restartable;
  }
}
