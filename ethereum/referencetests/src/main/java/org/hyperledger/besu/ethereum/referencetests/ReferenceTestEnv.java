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
 *
 */

package org.hyperledger.besu.ethereum.referencetests;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;

/** A memory holder for testing. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceTestEnv extends BlockHeader {

  public record EnvWithdrawal(
      @JsonProperty("index") String index,
      @JsonProperty("validatorIndex") String validatorIndex,
      @JsonProperty("address") String address,
      @JsonProperty("amount") String amount) {

    Withdrawal asWithdrawal() {
      return new Withdrawal(
          UInt64.fromHexString(index),
          UInt64.fromHexString(validatorIndex),
          Address.fromHexString(address),
          GWei.fromHexString(amount));
    }
  }

  private final String parentDifficulty;

  private final String parentBaseFee;

  private final String parentGasUsed;

  private final String parentGasLimit;

  private final String parentTimestamp;

  private final List<Withdrawal> withdrawals;

  /**
   * Public constructor.
   *
   * @param coinbase Coinbase/beneficiary for the mock block being tested.
   * @param difficulty Difficulty for the mock block being tested.
   * @param gasLimit Gas Limit for the mock block being tested.
   * @param number Block number for the mock block being tested.
   * @param baseFee Optional BaseFee for the mock block being tested.
   * @param timestamp Timestamp for the mock block being tested.
   * @param random Optional RANDAO or the mock block being tested.
   */
  @JsonCreator
  public ReferenceTestEnv(
      @JsonProperty("currentCoinbase") final String coinbase,
      @JsonProperty("currentDifficulty") final String difficulty,
      @JsonProperty("currentGasLimit") final String gasLimit,
      @JsonProperty("currentNumber") final String number,
      @JsonProperty("currentBaseFee") final String baseFee,
      @JsonProperty("currentTimestamp") final String timestamp,
      @JsonProperty("currentRandom") final String random,
      @JsonProperty("previousHash") final String previousHash,
      @JsonProperty("parentDifficulty") final String parentDifficulty,
      @JsonProperty("parentBaseFee") final String parentBaseFee,
      @JsonProperty("parentGasUsed") final String parentGasUsed,
      @JsonProperty("parentGasLimit") final String parentGasLimit,
      @JsonProperty("parentTimestamp") final String parentTimestamp,
      @JsonProperty("withdrawals") final List<EnvWithdrawal> withdrawals) {
    super(
        generateTestBlockHash(previousHash, number),
        Hash.EMPTY_LIST_HASH, // ommersHash
        Address.fromHexString(coinbase),
        Hash.EMPTY, // stateRoot
        Hash.EMPTY, // transactionsRoot
        Hash.EMPTY, // receiptsRoot
        new LogsBloomFilter(),
        difficulty == null ? null : Difficulty.fromHexOrDecimalString(difficulty),
        number == null ? 0 : Long.decode(number),
        gasLimit == null ? 15_000_000L : Long.decode(gasLimit),
        0L,
        timestamp == null ? 0L : Long.decode(timestamp),
        Bytes.EMPTY,
        Optional.ofNullable(baseFee).map(Wei::fromHexString).orElse(null),
        Optional.ofNullable(random).map(Difficulty::fromHexString).orElse(Difficulty.ZERO),
        0L,
        null, // withdrawalsRoot
        null,
        new MainnetBlockHeaderFunctions());
    this.parentDifficulty = parentDifficulty;
    this.parentBaseFee = parentBaseFee;
    this.parentGasUsed = parentGasUsed;
    this.parentGasLimit = parentGasLimit;
    this.parentTimestamp = parentTimestamp;
    this.withdrawals =
        withdrawals == null
            ? List.of()
            : withdrawals.stream().map(EnvWithdrawal::asWithdrawal).toList();
  }

  @Override
  public Difficulty getDifficulty() {
    return difficulty == null ? Difficulty.ZERO : super.getDifficulty();
  }

  private static Hash generateTestBlockHash(final String previousHash, final String number) {
    if (Strings.isNullOrEmpty(previousHash)) {
      if (number == null) {
        return Hash.EMPTY;
      } else {
        final byte[] bytes = Long.toString(Long.decode(number) - 1).getBytes(UTF_8);
        return Hash.hash(Bytes.wrap(bytes));
      }
    } else {
      return Hash.wrap(Bytes32.fromHexString(previousHash));
    }
  }

  public BlockHeader updateFromParentValues(final ProtocolSpec protocolSpec) {
    var builder =
        BlockHeaderBuilder.fromHeader(this)
            .blockHeaderFunctions(protocolSpec.getBlockHeaderFunctions());
    if (protocolSpec.getWithdrawalsProcessor().isPresent()) {
      builder.withdrawalsRoot(BodyValidation.withdrawalsRoot(withdrawals));
    }
    if ((baseFee == null || baseFee.isEmpty()) && protocolSpec.getFeeMarket().implementsBaseFee()) {
      builder.baseFee(
          ((BaseFeeMarket) protocolSpec.getFeeMarket())
              .computeBaseFee(
                  number,
                  Wei.fromHexString(parentBaseFee),
                  Long.parseLong(parentGasUsed),
                  gasLimit / 2));
    }
    if (difficulty == null && parentDifficulty != null) {
      builder.difficulty(
          Difficulty.of(
              protocolSpec
                  .getDifficultyCalculator()
                  .nextDifficulty(
                      timestamp,
                      BlockHeaderBuilder.createDefault()
                          .difficulty(Difficulty.fromHexOrDecimalString(parentDifficulty))
                          .number(number - 1)
                          .buildBlockHeader(),
                      null)));
    }

    return builder.buildBlockHeader();
  }

  public List<Withdrawal> getWithdrawals() {
    return withdrawals;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ReferenceTestEnv that)) return false;
    if (!super.equals(o)) return false;
    return Objects.equals(parentDifficulty, that.parentDifficulty)
        && Objects.equals(parentBaseFee, that.parentBaseFee)
        && Objects.equals(parentGasUsed, that.parentGasUsed)
        && Objects.equals(parentGasLimit, that.parentGasLimit)
        && Objects.equals(parentTimestamp, that.parentTimestamp)
        && Objects.equals(withdrawals, that.withdrawals);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        parentDifficulty,
        parentBaseFee,
        parentGasUsed,
        parentGasLimit,
        parentTimestamp,
        withdrawals);
  }
}
