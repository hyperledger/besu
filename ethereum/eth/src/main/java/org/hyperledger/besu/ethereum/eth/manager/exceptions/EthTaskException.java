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
package org.hyperledger.besu.ethereum.eth.manager.exceptions;

public class EthTaskException extends RuntimeException {

  private final FailureReason failureReason;

  EthTaskException(final FailureReason failureReason) {
    this("Task failed: " + failureReason.name(), failureReason);
  }

  EthTaskException(final String message, final FailureReason failureReason) {
    super(message);
    this.failureReason = failureReason;
  }

  public FailureReason reason() {
    return failureReason;
  }

  public enum FailureReason {
    PEER_DISCONNECTED,
    NO_AVAILABLE_PEERS,
    PEER_BREACHED_PROTOCOL,
    INCOMPLETE_RESULTS,
    MAX_RETRIES_REACHED
  }
}
