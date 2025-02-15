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
package org.hyperledger.besu.ethereum.mainnet.blockhash;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.mainnet.systemcall.BlockProcessingContext;
import org.hyperledger.besu.ethereum.mainnet.systemcall.SystemCallProcessor;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;

/**
 * Processes and stores historical block hashes in accordance with EIP-2935. This class is
 * responsible for managing the storage of block hashes to support EIP-2935, which introduces
 * historical block hash access in smart contracts.
 */
public class PragueBlockHashProcessor extends CancunBlockHashProcessor {
  private static final Address HISTORY_STORAGE_ADDRESS =
      Address.fromHexString("0x0000f90827f1c53a10cb7a02335b175320002935");

  protected final Address historyStorageAddress;

  /** Constructs a BlockHashProcessor. */
  public PragueBlockHashProcessor() {
    this(HISTORY_STORAGE_ADDRESS);
  }

  /**
   * Constructs a BlockHashProcessor with a specified history save window. This constructor is
   * primarily used for testing.
   *
   * @param historyStorageAddress the address of the contract storing the history
   */
  @VisibleForTesting
  public PragueBlockHashProcessor(final Address historyStorageAddress) {
    this.historyStorageAddress = historyStorageAddress;
  }

  @Override
  public Void process(final BlockProcessingContext context) {
    super.process(context);
    SystemCallProcessor processor =
        new SystemCallProcessor(context.getProtocolSpec().getTransactionProcessor());

    Bytes inputData = context.getBlockHeader().getParentHash();
    processor.process(historyStorageAddress, context, inputData);
    return null;
  }
}
