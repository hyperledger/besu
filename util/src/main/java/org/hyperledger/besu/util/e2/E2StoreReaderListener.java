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
package org.hyperledger.besu.util.e2;

/** A Listener interface for listening to an E2StoreReader */
public interface E2StoreReaderListener {
  /**
   * Handles the supplied E2ExecutionBlockHeader
   *
   * @param executionBlockHeader the E2ExecutionBlockHeader
   */
  void handleExecutionBlockHeader(E2ExecutionBlockHeader executionBlockHeader);

  /**
   * Handles the supplied E2ExecutionBlockBody
   *
   * @param executionBlockBody the E2ExecutionBlockBody
   */
  void handleExecutionBlockBody(E2ExecutionBlockBody executionBlockBody);

  /**
   * Handles the supplied E2ExecutionBlockReceipts
   *
   * @param executionBlockReceipts the E2ExecutionBlockReceipts
   */
  void handleExecutionBlockReceipts(E2ExecutionBlockReceipts executionBlockReceipts);

  /**
   * Handles the supplied E2BlockIndex
   *
   * @param blockIndex the E2BlockIndex
   */
  void handleBlockIndex(E2BlockIndex blockIndex);
}
