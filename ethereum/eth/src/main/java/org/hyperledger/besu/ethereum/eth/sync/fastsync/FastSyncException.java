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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

public class FastSyncException extends RuntimeException {

  private final FastSyncError error;

  public FastSyncException(final FastSyncError error) {
    super("Fast sync failed: " + error);
    this.error = error;
  }

  public FastSyncError getError() {
    return error;
  }

  public FastSyncException(final Throwable error) {
    super(error);
    this.error = FastSyncError.UNEXPECTED_ERROR;
  }
}
