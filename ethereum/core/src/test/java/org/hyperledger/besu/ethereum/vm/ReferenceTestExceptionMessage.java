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
package org.hyperledger.besu.ethereum.vm;

public enum ReferenceTestExceptionMessage {
  INVALID_GENESIS_RLP("genesisRLP in test != genesisRLP on remote client! (%s' != '%s'"),
  INVALID_LAST_BLOCK_HASH("lastblockhash does not match! remote: %s, test: %s");

  final String message;

  ReferenceTestExceptionMessage(final String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }
}
