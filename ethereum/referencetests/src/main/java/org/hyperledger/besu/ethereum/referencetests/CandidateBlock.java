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
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;

public abstract class CandidateBlock {

  protected final Bytes rlp;
  protected Boolean valid;

  protected CandidateBlock(final String rlp) {
    boolean blockValid = true;
    // The BLOCK__WrongCharAtRLP_0 test has an invalid character in its rlp string.
    Bytes rlpAttempt = null;
    try {
      rlpAttempt = Bytes.fromHexString(rlp);
    } catch (final IllegalArgumentException e) {
      blockValid = false;
    }
    this.rlp = rlpAttempt;
    this.valid = blockValid;
  }

  public Block getBlock() {
    final RLPInput input = new BytesValueRLPInput(rlp, false);
    input.enterList();
    final MainnetBlockHeaderFunctions blockHeaderFunctions = new MainnetBlockHeaderFunctions();
    final BlockHeader header = BlockHeader.readFrom(input, blockHeaderFunctions);
    final BlockBody body =
        new BlockBody(
            input.readList(Transaction::readFrom),
            input.readList(inputData -> BlockHeader.readFrom(inputData, blockHeaderFunctions)),
            input.isEndOfCurrentList()
                ? Optional.empty()
                : Optional.of(input.readList(Withdrawal::readFrom)));
    return new Block(header, body);
  }

  public boolean isValid() {
    return valid;
  }

  public abstract boolean areAllTransactionsValid();

  public boolean isExecutable() {
    return rlp != null;
  }
}
