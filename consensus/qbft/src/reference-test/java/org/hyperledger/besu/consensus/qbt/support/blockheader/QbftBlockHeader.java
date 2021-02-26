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
package org.hyperledger.besu.consensus.qbt.support.blockheader;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;

import org.apache.tuweni.bytes.Bytes;

public class QbftBlockHeader {
  private Hash parentHash;
  private Hash ommersHash;
  private Address coinbase;
  private Hash stateRoot;
  private Hash transactionsRoot;
  private Hash receiptsRoot;
  private LogsBloomFilter logsBloom;
  private Difficulty difficulty;
  private long number = -1L;
  private long gasLimit = -1L;
  private long gasUsed = -1L;
  private long timestamp = -1L;
  private Bytes extraData;
  private Hash mixHash;
  private Long nonce;

  public Hash getParentHash() {
    return parentHash;
  }

  public void setParentHash(final Hash parentHash) {
    this.parentHash = parentHash;
  }

  public Hash getOmmersHash() {
    return ommersHash;
  }

  public void setOmmersHash(final Hash ommersHash) {
    this.ommersHash = ommersHash;
  }

  public Address getCoinbase() {
    return coinbase;
  }

  public void setCoinbase(final Address coinbase) {
    this.coinbase = coinbase;
  }

  public Hash getStateRoot() {
    return stateRoot;
  }

  public void setStateRoot(final Hash stateRoot) {
    this.stateRoot = stateRoot;
  }

  public Hash getTransactionsRoot() {
    return transactionsRoot;
  }

  public void setTransactionsRoot(final Hash transactionsRoot) {
    this.transactionsRoot = transactionsRoot;
  }

  public Hash getReceiptsRoot() {
    return receiptsRoot;
  }

  public void setReceiptsRoot(final Hash receiptsRoot) {
    this.receiptsRoot = receiptsRoot;
  }

  public LogsBloomFilter getLogsBloom() {
    return logsBloom;
  }

  public void setLogsBloom(final LogsBloomFilter logsBloom) {
    this.logsBloom = logsBloom;
  }

  public Difficulty getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(final Difficulty difficulty) {
    this.difficulty = difficulty;
  }

  public long getNumber() {
    return number;
  }

  public void setNumber(final long number) {
    this.number = number;
  }

  public long getGasLimit() {
    return gasLimit;
  }

  public void setGasLimit(final long gasLimit) {
    this.gasLimit = gasLimit;
  }

  public long getGasUsed() {
    return gasUsed;
  }

  public void setGasUsed(final long gasUsed) {
    this.gasUsed = gasUsed;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
  }

  public Bytes getExtraData() {
    return extraData;
  }

  public void setExtraData(final Bytes extraData) {
    this.extraData = extraData;
  }

  public Hash getMixHash() {
    return mixHash;
  }

  public void setMixHash(final Hash mixHash) {
    this.mixHash = mixHash;
  }

  public Long getNonce() {
    return nonce;
  }

  public void setNonce(final Long nonce) {
    this.nonce = nonce;
  }

  public static BlockHeader convertToCoreBlockHeader(final QbftBlockHeader qbftBlockHeader) {
    return BlockHeaderBuilder.create()
        .parentHash(qbftBlockHeader.parentHash)
        .ommersHash(qbftBlockHeader.ommersHash)
        .coinbase(qbftBlockHeader.coinbase)
        .stateRoot(qbftBlockHeader.stateRoot)
        .transactionsRoot(qbftBlockHeader.transactionsRoot)
        .receiptsRoot(qbftBlockHeader.receiptsRoot)
        .logsBloom(qbftBlockHeader.logsBloom)
        .difficulty(qbftBlockHeader.difficulty)
        .number(qbftBlockHeader.number)
        .gasLimit(qbftBlockHeader.gasLimit)
        .gasUsed(qbftBlockHeader.gasUsed)
        .timestamp(qbftBlockHeader.timestamp)
        .extraData(qbftBlockHeader.extraData)
        .mixHash(qbftBlockHeader.mixHash)
        .nonce(qbftBlockHeader.nonce)
        .blockHeaderFunctions(BftBlockHeaderFunctions.forOnChainBlock())
        .buildBlockHeader();
  }
}
