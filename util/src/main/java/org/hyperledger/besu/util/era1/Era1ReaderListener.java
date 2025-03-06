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
package org.hyperledger.besu.util.era1;

/** A Listener interface for listening to an Era1Reader */
public interface Era1ReaderListener {
  /**
   * Handles the supplied Era1ExecutionBlockHeader
   *
   * @param executionBlockHeader the Era1ExecutionBlockHeader
   */
  void handleExecutionBlockHeader(Era1ExecutionBlockHeader executionBlockHeader);

  /**
   * Handles the supplied Era1ExecutionBlockBody
   *
   * @param executionBlockBody the Era1ExecutionBlockBody
   */
  void handleExecutionBlockBody(Era1ExecutionBlockBody executionBlockBody);

  /**
   * Handles the supplied Era1ExecutionBlockReceipts
   *
   * @param executionBlockReceipts the Era1ExecutionBlockReceipts
   */
  void handleExecutionBlockReceipts(Era1ExecutionBlockReceipts executionBlockReceipts);

  /**
   * Handles the supplied Era1BlockIndex
   *
   * @param blockIndex the Era1BlockIndex
   */
  void handleBlockIndex(Era1BlockIndex blockIndex);
}
