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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.datatypes.Hash;

/**
 * This class represents a private transaction that has been executed. Therefore, it contains the
 * original private transaction data plus information about the associated PMT and its block.
 */
public class ExecutedPrivateTransaction extends PrivateTransaction {

  private final Hash blockHash;
  private final long blockNumber;
  private final Hash pmtHash;
  private final int pmtIndex;
  private final String internalPrivacyGroup;

  public ExecutedPrivateTransaction(
      final Hash blockHash,
      final long blockNumber,
      final Hash pmtHash,
      final int pmtIndex,
      final String internalPrivacyGroupId,
      final PrivateTransaction privateTransaction) {
    super(privateTransaction);
    this.blockHash = blockHash;
    this.blockNumber = blockNumber;
    this.pmtHash = pmtHash;
    this.pmtIndex = pmtIndex;
    this.internalPrivacyGroup = internalPrivacyGroupId;
  }

  public Hash getBlockHash() {
    return blockHash;
  }

  public long getBlockNumber() {
    return blockNumber;
  }

  public Hash getPmtHash() {
    return pmtHash;
  }

  public int getPmtIndex() {
    return pmtIndex;
  }

  /**
   * Legacy transactions don't have the privacyGroupId as part of their RLP data. The internal
   * privacy group id is returned from the Enclave. We are keeping it separate from the
   * 'getPrivacyGroup()' to differentiate legacy transactions.
   *
   * @return the privacy group id
   */
  public String getInternalPrivacyGroup() {
    return internalPrivacyGroup;
  }
}
