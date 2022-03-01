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

import org.hyperledger.besu.plugin.Unstable;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * The minimum set of data for a BlockHeader, as defined in the <a href=
 * "https://ethereum.github.io/yellowpaper/paper.pdf">Ethereum Yellow Paper</a>.
 */
public interface BlockHeader {

  /**
   * The Keccak 256-bit hash of the parent block’s header, in its entirety.
   *
   * @return The Keccak 256-bit hash of the parent block’s header, in its entirety.
   */
  Hash getParentHash();

  /**
   * The Keccak 256-bit hash of the ommers list portion of this block.
   *
   * @return The Keccak 256-bit hash of the ommers list portion of this block.
   */
  Hash getOmmersHash();

  /**
   * The 160-bit address to which all fees collected from the successful mining of this block be
   * transferred.
   *
   * <p>The name in the yellow paper is beneficiary.
   *
   * @return The 160-bit address to which all fees collected from the successful mining of this
   *     block be transferred.
   */
  Address getCoinbase();

  /**
   * The Keccak 256-bit hash of the root node of the state trie, after all transactions are executed
   * and finalisations applied.
   *
   * @return The Keccak 256-bit hash of the root node of the state trie, after all transactions are
   *     executed and finalisations applied.
   */
  Hash getStateRoot();

  /**
   * The Keccak 256-bit hash of theroot node of the trie structure populated with each transaction
   * in the transactions list portion of the block.
   *
   * @return The Keccak 256-bit hash of theroot node of the trie structure populated with each
   *     transaction in the transactions list portion of the block.
   */
  Hash getTransactionsRoot();

  /**
   * The Keccak 256-bit hash of the root node of the trie structure populated with the receipts of
   * each transaction in the transactions list portion of the block.
   *
   * @return The Keccak 256-bit hash of the root node of the trie structure populated with the
   *     receipts of each transaction in the transactions list portion of the block.
   */
  Hash getReceiptsRoot();

  /**
   * The Bloom filter composed from indexable information (logger address and log topics) contained
   * in each log entry from the receipt of each transaction in the transactions list.
   *
   * @return The Bloom filter composed from indexable information (logger address and log topics)
   *     contained in each log entry from the receipt of each transaction in the transactions list.
   */
  Bytes getLogsBloom();

  /**
   * A scalar value corresponding to the difficulty level of this block. This can be calculated from
   * the previous block’s difficulty level and the timestamp.
   *
   * @return A UInt256 value corresponding to the difficulty level of this block. This can be
   *     calculated from the previous block’s difficulty level and the timestamp.
   */
  Quantity getDifficulty();

  /**
   * A scalar value equal to the number of ancestor blocks. The genesis block has a number of zero.
   *
   * @return A scalar value equal to the number of ancestor blocks. The genesis block has a number
   *     of zero.
   */
  long getNumber();

  /**
   * A scalar value equal to the current limit of gas expenditure per block.
   *
   * @return A scalar value equal to the current limit of gas expenditure per block.
   */
  long getGasLimit();

  /**
   * A scalar value equal to the total gas used in transactions in this block.
   *
   * @return A scalar value equal to the total gas used in transactions in this block.
   */
  long getGasUsed();

  /**
   * A scalar value equal to the reasonable output of Unix’s time() at this block’s inception.
   *
   * @return A scalar value equal to the reasonable output of Unix’s time() at this block’s
   *     inception.
   */
  long getTimestamp();

  /**
   * An arbitrary byte array containing data relevant to this block. This must be 32 bytes or fewer.
   *
   * @return An arbitrary byte array containing data relevant to this block. This must be 32 bytes
   *     or fewer.
   */
  Bytes getExtraData();

  /**
   * A 256-bit hash which, combined with the nonce, proves that a sufficient amount of computation
   * has been carried out on this block.
   *
   * @return A 256-bit hash which, combined with the nonce, proves that a sufficient amount of
   *     computation has been carried out on this block.
   */
  Hash getMixHash();

  /**
   * A 64-bit value which, combined with the mixhash, proves that a sufficient amount of computation
   * has been carried out on this block.
   *
   * @return A 64-bit value which, combined with the mixhash, proves that a sufficient amount of
   *     computation has been carried out on this block.
   */
  long getNonce();

  /**
   * The Keccak 256-bit hash of this header.
   *
   * @return The Keccak 256-bit hash of this header.
   */
  Hash getBlockHash();

  /**
   * The BASEFEE of this header.
   *
   * @return The BASEFEE of this header.
   */
  @Unstable
  default Optional<? extends Quantity> getBaseFee() {
    return Optional.empty();
  }

  /**
   * Optional 32 bytes of prevRandao data.
   *
   * @return Optional prevRandao bytes from this header.
   */
  default Optional<Bytes32> getPrevRandao() {
    return Optional.empty();
  }
}
