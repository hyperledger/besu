/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

/** A block header capable of being sealed. */
public class SealableBlockHeader extends ProcessableBlockHeader {
  protected final Hash ommersHash;

  protected final Hash stateRoot;

  protected final Hash transactionsRoot;

  protected final Hash receiptsRoot;

  protected final LogsBloomFilter logsBloom;

  protected final long gasUsed;

  protected final BytesValue extraData;

  protected SealableBlockHeader(
      final Hash parentHash,
      final Hash ommersHash,
      final Address coinbase,
      final Hash stateRoot,
      final Hash transactionsRoot,
      final Hash receiptsRoot,
      final LogsBloomFilter logsBloom,
      final UInt256 difficulty,
      final long number,
      final long gasLimit,
      final long gasUsed,
      final long timestamp,
      final BytesValue extraData) {
    super(parentHash, coinbase, difficulty, number, gasLimit, timestamp);
    this.ommersHash = ommersHash;
    this.stateRoot = stateRoot;
    this.transactionsRoot = transactionsRoot;
    this.receiptsRoot = receiptsRoot;
    this.logsBloom = logsBloom;
    this.gasUsed = gasUsed;
    this.extraData = extraData;
  }

  /**
   * Returns the block ommers list hash.
   *
   * @return the block ommers list hash
   */
  public Hash getOmmersHash() {
    return ommersHash;
  }

  /**
   * Returns the block world state root hash.
   *
   * @return the block world state root hash
   */
  public Hash getStateRoot() {
    return stateRoot;
  }

  /**
   * Returns the block transaction root hash.
   *
   * @return the block transaction root hash
   */
  public Hash getTransactionsRoot() {
    return transactionsRoot;
  }

  /**
   * Returns the block transaction receipt root hash.
   *
   * @return the block transaction receipt root hash
   */
  public Hash getReceiptsRoot() {
    return receiptsRoot;
  }

  /**
   * Returns the block logs bloom filter.
   *
   * @return the block logs bloom filter
   */
  public LogsBloomFilter getLogsBloom() {
    return logsBloom;
  }

  /**
   * Returns the total gas consumed by the executing the block.
   *
   * @return the total gas consumed by the executing the block
   */
  public long getGasUsed() {
    return gasUsed;
  }

  /**
   * Returns the unparsed extra data field.
   *
   * @return the raw bytes of the extra data field
   */
  public BytesValue getExtraData() {
    return extraData;
  }
}
