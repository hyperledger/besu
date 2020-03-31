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
package org.hyperledger.besu.plugin.data;

import java.util.List;

/** The minimum set of data for a AddedBlockContext. */
public interface AddedBlockContext {

  /**
   * A {@link BlockHeader} object.
   *
   * @return A {@link BlockHeader}
   */
  BlockHeader getBlockHeader();

  /**
   * A {@link BlockHeader} object.
   *
   * @return A {@link BlockHeader}
   */
  BlockBody getBlockBody();

  /**
   * A list of transaction receipts for the added block.
   *
   * @return A List of {@link TransactionReceipt}
   */
  List<? extends TransactionReceipt> getTransactionReceipts();
}
