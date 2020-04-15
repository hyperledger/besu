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
package org.hyperledger.besu.ethereum.core.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes.FEE_MARKET_TRANSACTIONS;
import static org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes.FEE_MARKET_TRANSITIONAL_TRANSACTIONS;
import static org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes.FRONTIER_TRANSACTIONS;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.rlp.RLP;

import org.apache.tuweni.bytes.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class EIP1559Test {

  private static final long FORK_BLOCK = 783L;
  private final EIP1559 eip1559 = new EIP1559(FORK_BLOCK);
  private final FeeMarket feeMarket = FeeMarket.eip1559();

  @Before
  public void setUp() {
    ExperimentalEIPs.eip1559Enabled = true;
  }

  @After
  public void reset() {
    ExperimentalEIPs.eip1559Enabled = ExperimentalEIPs.EIP1559_ENABLED_DEFAULT_VALUE;
  }

  @Test
  public void assertThatBaseFeeDecreasesWhenBelowTargetGasUsed() {
    assertThat(
            eip1559.computeBaseFee(
                feeMarket.getInitialBasefee(), feeMarket.getTargetGasUsed() - 1000000L))
        .isLessThan(feeMarket.getInitialBasefee())
        .isEqualTo(987500000L);
  }

  @Test
  public void assertThatBaseFeeIncreasesWhenAboveTargetGasUsed() {
    assertThat(
            eip1559.computeBaseFee(
                feeMarket.getInitialBasefee(), feeMarket.getTargetGasUsed() + 1000000L))
        .isGreaterThan(feeMarket.getInitialBasefee())
        .isEqualTo(1012500000L);
  }

  @Test
  public void assertThatBaseFeeDoesNotChangeWhenAtTargetGasUsed() {
    assertThat(eip1559.computeBaseFee(feeMarket.getInitialBasefee(), feeMarket.getTargetGasUsed()))
        .isEqualTo(feeMarket.getInitialBasefee());
  }

  @Test
  public void isValidBaseFee() {
    assertThat(eip1559.isValidBaseFee(feeMarket.getInitialBasefee(), 1012500000L)).isTrue();
  }

  @Test
  public void isNotValidBaseFee() {
    assertThat(
            eip1559.isValidBaseFee(
                feeMarket.getInitialBasefee(), feeMarket.getInitialBasefee() * 15L / 10L))
        .isFalse();
  }

  @Test
  public void eip1559GasPool() {
    assertThat(eip1559.eip1559GasPool(FORK_BLOCK + 1))
        .isEqualTo((feeMarket.getMaxGas() / 2) + feeMarket.getGasIncrementAmount());
    assertThat(eip1559.eip1559GasPool(FORK_BLOCK + 1) + eip1559.legacyGasPool(FORK_BLOCK + 1))
        .isEqualTo(feeMarket.getMaxGas());
  }

  @Test
  public void legacyGasPool() {
    assertThat(eip1559.legacyGasPool(FORK_BLOCK + 1))
        .isEqualTo((feeMarket.getMaxGas() / 2) - feeMarket.getGasIncrementAmount());
    assertThat(eip1559.eip1559GasPool(FORK_BLOCK + 1) + eip1559.legacyGasPool(FORK_BLOCK + 1))
        .isEqualTo(feeMarket.getMaxGas());
  }

  @Test
  public void givenBlockAfterFork_whenIsEIP1559_returnsTrue() {
    assertThat(eip1559.isEIP1559(FORK_BLOCK + 1)).isTrue();
  }

  @Test
  public void givenBlockABeforeFork_whenIsEIP1559_returnsFalse() {
    assertThat(eip1559.isEIP1559(FORK_BLOCK - 1)).isFalse();
  }

  @Test
  public void givenBlockAfterEIPFinalized_whenIsEIP1559Finalized_returnsTrue() {
    assertThat(eip1559.isEIP1559Finalized(FORK_BLOCK + feeMarket.getDecayRange())).isTrue();
  }

  @Test
  public void givenBlockBeforeEIPFinalized_whenIsEIP1559Finalized_returnsFalse() {
    assertThat(eip1559.isEIP1559Finalized(FORK_BLOCK + feeMarket.getDecayRange() - 1)).isFalse();
  }

  @Test
  public void givenForkBlock_whenIsForkBlock_thenReturnsTrue() {
    assertThat(eip1559.isForkBlock(FORK_BLOCK)).isTrue();
  }

  @Test
  public void givenNotForkBlock_whenIsForkBlock_thenReturnsFalse() {
    assertThat(eip1559.isForkBlock(FORK_BLOCK + 1)).isFalse();
  }

  @Test
  public void getForkBlock() {
    assertThat(eip1559.getForkBlock()).isEqualTo(FORK_BLOCK);
  }

  @Test
  public void givenValidLegacyTransaction_whenBeforeForkBlock_thenReturnsTrue() {
    assertThat(eip1559.isValidFormat(TransactionFixture.LEGACY, FRONTIER_TRANSACTIONS)).isTrue();
  }

  @Test
  public void givenValidLegacyTransaction_whenEIP1559Phase1_thenReturnsTrue() {
    assertThat(
            eip1559.isValidFormat(TransactionFixture.LEGACY, FEE_MARKET_TRANSITIONAL_TRANSACTIONS))
        .isTrue();
  }

  @Test
  public void givenValidLegacyTransaction_whenEIP1559Finalized_thenReturnsFalse() {
    assertThat(eip1559.isValidFormat(TransactionFixture.LEGACY, FEE_MARKET_TRANSACTIONS)).isFalse();
  }

  @Test
  public void givenValidEIP1559Transaction_whenAfterForkBlock_thenReturnsTrue() {
    assertThat(
            eip1559.isValidFormat(TransactionFixture.EIP1559, FEE_MARKET_TRANSITIONAL_TRANSACTIONS))
        .isTrue();
  }

  @Test
  public void givenValidEIP1559Transaction_whenEIP1559Finalized_thenReturnsTrue() {
    assertThat(eip1559.isValidFormat(TransactionFixture.EIP1559, FEE_MARKET_TRANSACTIONS)).isTrue();
  }

  @Test
  public void givenValidEIP1559Transaction_whenBeforeFork_thenReturnsFalse() {
    assertThat(eip1559.isValidFormat(TransactionFixture.EIP1559, FRONTIER_TRANSACTIONS)).isFalse();
  }

  private static class TransactionFixture {
    private static final Transaction LEGACY =
        Transaction.readFrom(
            RLP.input(
                Bytes.fromHexString(
                    "0xf901fc8032830138808080b901ae60056013565b6101918061001d6000396000f35b3360008190555056006001600060e060020a6000350480630a874df61461003a57806341c0e1b514610058578063a02b161e14610066578063dbbdf0831461007757005b610045600435610149565b80600160a060020a031660005260206000f35b610060610161565b60006000f35b6100716004356100d4565b60006000f35b61008560043560243561008b565b60006000f35b600054600160a060020a031632600160a060020a031614156100ac576100b1565b6100d0565b8060018360005260205260406000208190555081600060005260206000a15b5050565b600054600160a060020a031633600160a060020a031614158015610118575033600160a060020a0316600182600052602052604060002054600160a060020a031614155b61012157610126565b610146565b600060018260005260205260406000208190555080600060005260206000a15b50565b60006001826000526020526040600020549050919050565b600054600160a060020a031633600160a060020a0316146101815761018f565b600054600160a060020a0316ff5b561ca0c5689ed1ad124753d54576dfb4b571465a41900a1dff4058d8adf16f752013d0a01221cbd70ec28c94a3b55ec771bcbc70778d6ee0b51ca7ea9514594c861b1884")));

    private static final Transaction EIP1559 =
        Transaction.readFrom(
            RLP.input(
                Bytes.fromHexString(
                    "0xf902028032830138808080b901ae60056013565b6101918061001d6000396000f35b3360008190555056006001600060e060020a6000350480630a874df61461003a57806341c0e1b514610058578063a02b161e14610066578063dbbdf0831461007757005b610045600435610149565b80600160a060020a031660005260206000f35b610060610161565b60006000f35b6100716004356100d4565b60006000f35b61008560043560243561008b565b60006000f35b600054600160a060020a031632600160a060020a031614156100ac576100b1565b6100d0565b8060018360005260205260406000208190555081600060005260206000a15b5050565b600054600160a060020a031633600160a060020a031614158015610118575033600160a060020a0316600182600052602052604060002054600160a060020a031614155b61012157610126565b610146565b600060018260005260205260406000208190555080600060005260206000a15b50565b60006001826000526020526040600020549050919050565b600054600160a060020a031633600160a060020a0316146101815761018f565b600054600160a060020a0316ff5b5682020f8201711ca0c5689ed1ad124753d54576dfb4b571465a41900a1dff4058d8adf16f752013d0a01221cbd70ec28c94a3b55ec771bcbc70778d6ee0b51ca7ea9514594c861b1884")));
  }
}
