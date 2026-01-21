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
package org.hyperledger.besu.evm.log;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Strategy interface for emitting ETH transfer logs.
 *
 * <p>This allows different implementations for different protocol versions:
 *
 * <ul>
 *   <li>{@link #NOOP} - No-op implementation for pre-Amsterdam forks
 *   <li>{@link EIP7708TransferLogEmitter} - EIP-7708 compliant implementation for Amsterdam+
 * </ul>
 */
@FunctionalInterface
public interface TransferLogEmitter {

  /**
   * Emit a transfer log for an ETH value transfer.
   *
   * @param frame the message frame to add the log to
   * @param from the sender address
   * @param to the recipient address
   * @param value the amount transferred in Wei
   */
  void emitTransferLog(MessageFrame frame, Address from, Address to, Wei value);

  /** No-op implementation. Used for pre-Amsterdam forks. */
  TransferLogEmitter NOOP = (frame, from, to, value) -> {};
}
