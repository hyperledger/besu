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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockValueCalculator;
import org.hyperledger.besu.ethereum.core.BlockWithReceipts;
import org.hyperledger.besu.ethereum.core.Request;

import java.util.List;
import java.util.Optional;

/** Wrapper for payload plus extra info. */
public class PayloadWrapper {
  private final PayloadIdentifier payloadIdentifier;
  private final BlockWithReceipts blockWithReceipts;
  private final Wei blockValue;
  private final Optional<List<Request>> requests;

  /**
   * Construct a wrapper with the following fields.
   *
   * @param payloadIdentifier Payload identifier
   * @param blockWithReceipts Block with receipts
   */
  public PayloadWrapper(
      final PayloadIdentifier payloadIdentifier,
      final BlockWithReceipts blockWithReceipts,
      final Optional<List<Request>> requests) {
    this.blockWithReceipts = blockWithReceipts;
    this.payloadIdentifier = payloadIdentifier;
    this.blockValue = BlockValueCalculator.calculateBlockValue(blockWithReceipts);
    this.requests = requests;
  }

  /**
   * Get the block value
   *
   * @return block value in Wei
   */
  public Wei blockValue() {
    return blockValue;
  }

  /**
   * Get this payload identifier
   *
   * @return payload identifier
   */
  public PayloadIdentifier payloadIdentifier() {
    return payloadIdentifier;
  }

  /**
   * Get the block with receipts
   *
   * @return block with receipts
   */
  public BlockWithReceipts blockWithReceipts() {
    return blockWithReceipts;
  }

  /**
   * Get the requests
   *
   * @return requests
   */
  public Optional<List<Request>> requests() {
    return requests;
  }
}
