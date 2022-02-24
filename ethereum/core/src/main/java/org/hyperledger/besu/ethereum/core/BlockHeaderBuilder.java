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
package org.hyperledger.besu.ethereum.core;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.time.Instant;
import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** A utility class for building block headers. */
public class BlockHeaderBuilder {

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

  private Wei baseFee = null;

  private Bytes32 mixHashOrPrevRandao = null;

  private BlockHeaderFunctions blockHeaderFunctions;

  // A nonce can be any value so we use the OptionalLong
  // instead of an invalid identifier such as -1.
  private OptionalLong nonce = OptionalLong.empty();

  public static BlockHeaderBuilder create() {
    return new BlockHeaderBuilder();
  }

  public static BlockHeaderBuilder fromHeader(final BlockHeader header) {
    return create()
        .parentHash(header.getParentHash())
        .ommersHash(header.getOmmersHash())
        .coinbase(header.getCoinbase())
        .stateRoot(header.getStateRoot())
        .transactionsRoot(header.getTransactionsRoot())
        .receiptsRoot(header.getReceiptsRoot())
        .logsBloom(header.getLogsBloom())
        .difficulty(header.getDifficulty())
        .number(header.getNumber())
        .gasLimit(header.getGasLimit())
        .gasUsed(header.getGasUsed())
        .timestamp(header.getTimestamp())
        .extraData(header.getExtraData())
        .baseFee(header.getBaseFee().orElse(null))
        .mixHash(header.getMixHash())
        .nonce(header.getNonce())
        .prevRandao(header.getPrevRandao().orElse(null));
  }

  public static BlockHeaderBuilder fromBuilder(final BlockHeaderBuilder fromBuilder) {
    final BlockHeaderBuilder toBuilder =
        create()
            .parentHash(fromBuilder.parentHash)
            .ommersHash(fromBuilder.ommersHash)
            .coinbase(fromBuilder.coinbase)
            .stateRoot(fromBuilder.stateRoot)
            .transactionsRoot(fromBuilder.transactionsRoot)
            .receiptsRoot(fromBuilder.receiptsRoot)
            .logsBloom(fromBuilder.logsBloom)
            .difficulty(fromBuilder.difficulty)
            .number(fromBuilder.number)
            .gasLimit(fromBuilder.gasLimit)
            .gasUsed(fromBuilder.gasUsed)
            .timestamp(fromBuilder.timestamp)
            .extraData(fromBuilder.extraData)
            .baseFee(fromBuilder.baseFee)
            .prevRandao(fromBuilder.mixHashOrPrevRandao)
            .blockHeaderFunctions(fromBuilder.blockHeaderFunctions);
    toBuilder.nonce = fromBuilder.nonce;
    return toBuilder;
  }

  public BlockHeader buildBlockHeader() {
    validateBlockHeader();

    return new BlockHeader(
        parentHash,
        ommersHash,
        coinbase,
        stateRoot,
        transactionsRoot,
        receiptsRoot,
        logsBloom,
        difficulty,
        number,
        gasLimit,
        gasUsed,
        timestamp < 0 ? Instant.now().getEpochSecond() : timestamp,
        extraData,
        baseFee,
        mixHashOrPrevRandao,
        nonce.getAsLong(),
        blockHeaderFunctions);
  }

  public ProcessableBlockHeader buildProcessableBlockHeader() {
    validateProcessableBlockHeader();

    return new ProcessableBlockHeader(
        parentHash,
        coinbase,
        difficulty,
        number,
        gasLimit,
        timestamp,
        baseFee,
        mixHashOrPrevRandao);
  }

  public SealableBlockHeader buildSealableBlockHeader() {
    validateSealableBlockHeader();

    return new SealableBlockHeader(
        parentHash,
        ommersHash,
        coinbase,
        stateRoot,
        transactionsRoot,
        receiptsRoot,
        logsBloom,
        difficulty,
        number,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFee,
        mixHashOrPrevRandao);
  }

  private void validateBlockHeader() {
    validateSealableBlockHeader();
    checkState(this.mixHashOrPrevRandao != null, "Missing mixHash or prevRandao");
    checkState(this.nonce.isPresent(), "Missing nonce");
    checkState(this.blockHeaderFunctions != null, "Missing blockHeaderFunctions");
  }

  private void validateProcessableBlockHeader() {
    checkState(this.parentHash != null, "Missing parent hash");
    checkState(this.coinbase != null, "Missing coinbase");
    checkState(this.difficulty != null, "Missing block difficulty");
    checkState(this.number > -1L, "Missing block number");
    checkState(this.gasLimit > -1L, "Missing gas limit");
    checkState(this.timestamp > -1L, "Missing timestamp");
  }

  private void validateSealableBlockHeader() {
    validateProcessableBlockHeader();
    checkState(this.ommersHash != null, "Missing ommers hash");
    checkState(this.stateRoot != null, "Missing state root");
    checkState(this.transactionsRoot != null, "Missing transaction root");
    checkState(this.receiptsRoot != null, "Missing receipts root");
    checkState(this.logsBloom != null, "Missing logs bloom filter");
    checkState(this.gasUsed > -1L, "Missing gas used");
    checkState(this.extraData != null, "Missing extra data field");
  }

  public BlockHeaderBuilder populateFrom(final ProcessableBlockHeader processableBlockHeader) {
    checkNotNull(processableBlockHeader);
    parentHash(processableBlockHeader.getParentHash());
    coinbase(processableBlockHeader.getCoinbase());
    difficulty(processableBlockHeader.getDifficulty());
    number(processableBlockHeader.getNumber());
    gasLimit(processableBlockHeader.getGasLimit());
    timestamp(processableBlockHeader.getTimestamp());
    baseFee(processableBlockHeader.getBaseFee().orElse(null));
    processableBlockHeader.getPrevRandao().ifPresent(this::prevRandao);
    return this;
  }

  public BlockHeaderBuilder populateFrom(final SealableBlockHeader sealableBlockHeader) {
    checkNotNull(sealableBlockHeader);
    parentHash(sealableBlockHeader.getParentHash());
    ommersHash(sealableBlockHeader.getOmmersHash());
    coinbase(sealableBlockHeader.getCoinbase());
    stateRoot(sealableBlockHeader.getStateRoot());
    transactionsRoot(sealableBlockHeader.getTransactionsRoot());
    receiptsRoot(sealableBlockHeader.getReceiptsRoot());
    logsBloom(sealableBlockHeader.getLogsBloom());
    difficulty(sealableBlockHeader.getDifficulty());
    number(sealableBlockHeader.getNumber());
    gasLimit(sealableBlockHeader.getGasLimit());
    gasUsed(sealableBlockHeader.getGasUsed());
    timestamp(sealableBlockHeader.getTimestamp());
    extraData(sealableBlockHeader.getExtraData());
    baseFee(sealableBlockHeader.getBaseFee().orElse(null));
    sealableBlockHeader.getPrevRandao().ifPresent(this::prevRandao);
    return this;
  }

  public BlockHeaderBuilder parentHash(final Hash hash) {
    checkNotNull(hash);
    this.parentHash = hash;
    return this;
  }

  public BlockHeaderBuilder ommersHash(final Hash hash) {
    checkNotNull(hash);
    this.ommersHash = hash;
    return this;
  }

  public BlockHeaderBuilder coinbase(final Address address) {
    checkNotNull(address);
    this.coinbase = address;
    return this;
  }

  public BlockHeaderBuilder stateRoot(final Hash hash) {
    checkNotNull(hash);
    this.stateRoot = hash;
    return this;
  }

  public BlockHeaderBuilder transactionsRoot(final Hash hash) {
    checkNotNull(hash);
    this.transactionsRoot = hash;
    return this;
  }

  public BlockHeaderBuilder receiptsRoot(final Hash hash) {
    checkNotNull(hash);
    this.receiptsRoot = hash;
    return this;
  }

  public BlockHeaderBuilder logsBloom(final LogsBloomFilter filter) {
    checkNotNull(filter);
    this.logsBloom = filter;
    return this;
  }

  public BlockHeaderBuilder difficulty(final Difficulty difficulty) {
    checkNotNull(difficulty);
    this.difficulty = difficulty;
    return this;
  }

  public BlockHeaderBuilder number(final long number) {
    checkArgument(number >= 0L);
    this.number = number;
    return this;
  }

  public BlockHeaderBuilder gasLimit(final long gasLimit) {
    checkArgument(gasLimit >= 0L);
    this.gasLimit = gasLimit;
    return this;
  }

  public BlockHeaderBuilder gasUsed(final long gasUsed) {
    checkArgument(gasUsed > -1L);
    this.gasUsed = gasUsed;
    return this;
  }

  public BlockHeaderBuilder timestamp(final long timestamp) {
    checkArgument(timestamp >= 0);
    this.timestamp = timestamp;
    return this;
  }

  public BlockHeaderBuilder extraData(final Bytes data) {
    checkNotNull(data);

    this.extraData = data;
    return this;
  }

  public BlockHeaderBuilder mixHash(final Hash mixHash) {
    checkNotNull(mixHash);
    this.mixHashOrPrevRandao = mixHash;
    return this;
  }

  public BlockHeaderBuilder nonce(final long nonce) {
    this.nonce = OptionalLong.of(nonce);
    return this;
  }

  public BlockHeaderBuilder blockHeaderFunctions(final BlockHeaderFunctions blockHeaderFunctions) {
    this.blockHeaderFunctions = blockHeaderFunctions;
    return this;
  }

  public BlockHeaderBuilder baseFee(final Wei baseFee) {
    this.baseFee = baseFee;
    return this;
  }

  public BlockHeaderBuilder prevRandao(final Bytes32 prevRandao) {
    if (prevRandao != null) {
      this.mixHashOrPrevRandao = prevRandao;
    }
    return this;
  }
}
