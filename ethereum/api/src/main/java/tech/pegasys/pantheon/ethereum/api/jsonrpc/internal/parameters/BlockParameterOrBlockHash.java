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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Represents a block parameter that can be a special value ("pending", "earliest", "latest") or a
 * number formatted as a hex string or a block hash.
 *
 * <p>When distinguishing between a hash and a number it is presumed that a hash won't have three
 * quarters of the leading bytes as zero. This is fined for block hashes but not for precompiled
 * contracts.
 */
public class BlockParameterOrBlockHash {

  private final BlockParameterType type;
  private final OptionalLong number;
  private final Optional<Hash> blockHash;

  @JsonCreator
  public BlockParameterOrBlockHash(final String value) {
    final String normalizedValue = value.toLowerCase();

    if (Objects.equals(normalizedValue, "earliest")) {
      type = BlockParameterType.EARLIEST;
      number = OptionalLong.of(BlockHeader.GENESIS_BLOCK_NUMBER);
      blockHash = Optional.empty();
    } else if (Objects.equals(normalizedValue, "latest")) {
      type = BlockParameterType.LATEST;
      number = OptionalLong.empty();
      blockHash = Optional.empty();
    } else if (Objects.equals(normalizedValue, "pending")) {
      type = BlockParameterType.PENDING;
      number = OptionalLong.empty();
      blockHash = Optional.empty();
    } else if (normalizedValue.length() > 16) {
      type = BlockParameterType.HASH;
      number = OptionalLong.empty();
      blockHash = Optional.of(Hash.fromHexStringLenient(normalizedValue));
    } else {
      type = BlockParameterType.NUMERIC;
      number = OptionalLong.of(Long.decode(value));
      blockHash = Optional.empty();
    }
  }

  public OptionalLong getNumber() {
    return number;
  }

  public Optional<Hash> getHash() {
    return blockHash;
  }

  public boolean isPending() {
    return this.type == BlockParameterType.PENDING;
  }

  public boolean isLatest() {
    return this.type == BlockParameterType.LATEST;
  }

  public boolean isEarliest() {
    return this.type == BlockParameterType.EARLIEST;
  }

  public boolean isNumeric() {
    return this.type == BlockParameterType.NUMERIC;
  }

  public boolean getBlockHash() {
    return this.type == BlockParameterType.HASH;
  }

  private enum BlockParameterType {
    EARLIEST,
    LATEST,
    PENDING,
    NUMERIC,
    HASH
  }
}
