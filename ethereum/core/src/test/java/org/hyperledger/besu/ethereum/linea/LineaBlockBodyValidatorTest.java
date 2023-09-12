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
package org.hyperledger.besu.ethereum.linea;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LineaBlockBodyValidatorTest {
  private static final int TX_MAX_CALLDATA_SIZE = 1000;
  private static final int BLOCK_MAX_CALLDATA_SIZE = TX_MAX_CALLDATA_SIZE * 2;

  private static final KeyPair keyPair = SignatureAlgorithmFactory.getInstance().generateKeyPair();

  @Mock ProtocolSpec protocolSpec;
  @Mock ProtocolSchedule protocolSchedule;
  @Mock Block block;
  @Mock BlockHeader blockHeader;

  LineaBlockBodyValidator blockBodyValidator;

  @BeforeEach
  public void setup() {
    when(protocolSpec.getCalldataLimits())
        .thenReturn(new CalldataLimits(TX_MAX_CALLDATA_SIZE, BLOCK_MAX_CALLDATA_SIZE));

    when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);

    when(block.getHeader()).thenReturn(blockHeader);

    blockBodyValidator = new LineaBlockBodyValidator(protocolSchedule);
  }

  @Test
  public void noTransactions() {
    when(block.getBody()).thenReturn(new BlockBody(List.of(), List.of()));

    assertThat(blockBodyValidator.validateCalldataLimit(block)).isTrue();
  }

  @Test
  public void oneTransactionBelowBothLimits() {
    when(block.getBody())
        .thenReturn(
            new BlockBody(
                List.of(createCalldataTransaction(1, TX_MAX_CALLDATA_SIZE - 1)), List.of()));

    assertThat(blockBodyValidator.validateCalldataLimit(block)).isTrue();
  }

  @Test
  public void oneTransactionBelowBlockLimitAndAboveTxLimit() {
    when(block.getBody())
        .thenReturn(
            new BlockBody(
                List.of(createCalldataTransaction(1, TX_MAX_CALLDATA_SIZE + 1)), List.of()));

    assertThat(blockBodyValidator.validateCalldataLimit(block)).isFalse();
  }

  @Test
  public void oneTransactionAboveBothLimits() {
    when(block.getBody())
        .thenReturn(
            new BlockBody(
                List.of(createCalldataTransaction(1, BLOCK_MAX_CALLDATA_SIZE + 1)), List.of()));

    assertThat(blockBodyValidator.validateCalldataLimit(block)).isFalse();
  }

  @Test
  public void moreTransactionsBelowBothLimits() {
    final int numTxs = 5;
    final int singleTxCalldataSize =
        Math.min(TX_MAX_CALLDATA_SIZE - 1, BLOCK_MAX_CALLDATA_SIZE / numTxs);

    when(block.getBody())
        .thenReturn(
            new BlockBody(
                IntStream.range(0, numTxs)
                    .mapToObj(i -> createCalldataTransaction(i, singleTxCalldataSize))
                    .toList(),
                List.of()));

    assertThat(blockBodyValidator.validateCalldataLimit(block)).isTrue();
  }

  @Test
  public void moreTransactionsBelowTxLimitButCumulativeAboveBlockLimit() {
    final int numTxs = 5;
    final int singleTxCalldataSize = TX_MAX_CALLDATA_SIZE - 1;

    when(block.getBody())
        .thenReturn(
            new BlockBody(
                IntStream.range(0, numTxs)
                    .mapToObj(i -> createCalldataTransaction(i, singleTxCalldataSize))
                    .toList(),
                List.of()));

    assertThat(blockBodyValidator.validateCalldataLimit(block)).isFalse();
  }

  private Transaction createCalldataTransaction(
      final int transactionNumber, final int payloadSize) {
    return createCalldataTransaction(transactionNumber, Bytes.random(payloadSize));
  }

  private Transaction createCalldataTransaction(final int transactionNumber, final Bytes payload) {
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .gasLimit(10)
        .maxFeePerGas(Wei.ZERO)
        .maxPriorityFeePerGas(Wei.ZERO)
        .nonce(transactionNumber)
        .payload(payload)
        .to(Address.ID)
        .value(Wei.of(transactionNumber))
        .sender(Address.ID)
        .chainId(BigInteger.ONE)
        .guessType()
        .signAndBuild(keyPair);
  }
}
