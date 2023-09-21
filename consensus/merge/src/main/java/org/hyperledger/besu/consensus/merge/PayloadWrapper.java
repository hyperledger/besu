/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;

class PayloadWrapper {
  /** The Payload identifier. */
  final PayloadIdentifier payloadIdentifier;
  /** The Block with receipts. */
  final BlockWithReceipts blockWithReceipts;

  /**
   * Instantiates a new PayloadWrapper.
   *
   * @param payloadIdentifier the payload identifier
   * @param blockWithReceipts the block with receipts
   */
  PayloadWrapper(
      final PayloadIdentifier payloadIdentifier, final BlockWithReceipts blockWithReceipts) {
    this.payloadIdentifier = payloadIdentifier;
    this.blockWithReceipts = blockWithReceipts;
  }
}
