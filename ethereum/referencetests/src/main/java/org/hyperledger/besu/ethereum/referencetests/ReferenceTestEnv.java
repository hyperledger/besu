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
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

/** A memory holder for testing. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceTestEnv extends BlockHeader {

  /**
   * Public constructor.
   *
   * @param coinbase Coinbase/beneficiary for the mock block being tested.
   * @param difficulty Difficulty for the mock block being tested.
   * @param gasLimit Gas Limit for the mock block being tested.
   * @param number Block number for the mock block being tested.
   * @param baseFee Optional BaseFee for the mock block being tested.
   * @param timestamp Timestamp for the mock block being tested.
   */
  @JsonCreator
  public ReferenceTestEnv(
      @JsonProperty("currentCoinbase") final String coinbase,
      @JsonProperty(value = "currentDifficulty", required = false) final String difficulty,
      @JsonProperty("currentGasLimit") final String gasLimit,
      @JsonProperty("currentNumber") final String number,
      @JsonProperty(value = "currentBaseFee", required = false) final String baseFee,
      @JsonProperty("currentTimestamp") final String timestamp) {
    super(
        generateTestBlockHash(Long.decode(number) - 1),
        Hash.EMPTY, // ommersHash
        Address.fromHexString(coinbase),
        Hash.EMPTY, // stateRoot
        Hash.EMPTY, // transactionsRoot
        Hash.EMPTY, // receiptsRoot
        new LogsBloomFilter(),
        Optional.ofNullable(difficulty).map(Difficulty::fromHexString).orElse(Difficulty.ZERO),
        Long.decode(number),
        Long.decode(gasLimit),
        0L,
        Long.decode(timestamp),
        Bytes.EMPTY,
        Optional.ofNullable(baseFee).map(Wei::fromHexString).orElse(null),
        Hash.ZERO,
        0L,
        new MainnetBlockHeaderFunctions());
  }

  private static Hash generateTestBlockHash(final long number) {
    final byte[] bytes = Long.toString(number).getBytes(UTF_8);
    return Hash.hash(Bytes.wrap(bytes));
  }
}
