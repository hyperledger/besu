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

import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.vm.TestBlockchain;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A memory mock for testing. */
@JsonIgnoreProperties("previousHash")
public class BlockHeaderMock extends BlockHeader {

  /**
   * Public constructor.
   *
   * @param coinbase The beneficiary address.
   * @param gasLimit The gas limit of the current block.
   * @param number The number to execute.
   */
  @JsonCreator
  public BlockHeaderMock(
      @JsonProperty("currentCoinbase") final String coinbase,
      @JsonProperty("currentDifficulty") final String difficulty,
      @JsonProperty("currentGasLimit") final String gasLimit,
      @JsonProperty("currentNumber") final String number,
      @JsonProperty("currentTimestamp") final String timestamp) {
    super(
        TestBlockchain.generateTestBlockHash(Long.decode(number) - 1),
        Hash.EMPTY, // ommersHash
        Address.fromHexString(coinbase),
        Hash.EMPTY, // stateRoot
        Hash.EMPTY, // transactionsRoot
        Hash.EMPTY, // receiptsRoot
        new LogsBloomFilter(),
        UInt256.fromHexString(difficulty),
        Long.decode(number),
        Long.decode(gasLimit),
        0L,
        Long.decode(timestamp),
        BytesValue.EMPTY,
        Hash.ZERO,
        0L,
        new MainnetBlockHeaderFunctions());
  }
}
