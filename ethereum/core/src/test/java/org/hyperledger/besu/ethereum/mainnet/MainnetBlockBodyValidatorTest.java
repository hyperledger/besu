/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.NONE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Blob;
import org.hyperledger.besu.datatypes.BlobProofBundle;
import org.hyperledger.besu.datatypes.GWei;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.KZGProof;
import org.hyperledger.besu.ethereum.core.BlobTestFixture;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.requests.RequestsValidator;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.evm.precompile.KZGPointEvalPrecompiledContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import ethereum.ckzg4844.CKZG4844JNI;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.units.bigints.UInt64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MainnetBlockBodyValidatorTest {
  private final BlockDataGenerator blockDataGenerator = new BlockDataGenerator();
  private final BlockchainSetupUtil blockchainSetupUtil = BlockchainSetupUtil.forMainnet();
  private final List<Withdrawal> withdrawals =
      List.of(new Withdrawal(UInt64.ONE, UInt64.ONE, Address.ZERO, GWei.ONE));

  @Mock private ProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private WithdrawalsValidator withdrawalsValidator;
  @Mock private RequestsValidator requestValidator;

  @BeforeEach
  public void setUp() {
    lenient().when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);

    lenient().when(protocolSpec.getWithdrawalsValidator()).thenReturn(withdrawalsValidator);
    lenient().when(withdrawalsValidator.validateWithdrawals(any())).thenReturn(true);
    lenient().when(withdrawalsValidator.validateWithdrawalsRoot(any())).thenReturn(true);

    lenient().when(protocolSpec.getRequestsValidator()).thenReturn(requestValidator);
    lenient().when(requestValidator.validate(any())).thenReturn(true);
  }

  @Test
  void validatesWithdrawals() {
    final Block block =
        blockDataGenerator.block(
            new BlockOptions()
                .setBlockNumber(1)
                .setGasUsed(0)
                .hasTransactions(false)
                .hasOmmers(false)
                .setReceiptsRoot(BodyValidation.receiptsRoot(emptyList()))
                .setLogsBloom(LogsBloomFilter.empty())
                .setParentHash(blockchainSetupUtil.getBlockchain().getChainHeadHash())
                .setWithdrawals(Optional.of(withdrawals))
                .setWithdrawalsRoot(BodyValidation.withdrawalsRoot(withdrawals)));
    blockchainSetupUtil.getBlockchain().appendBlock(block, Collections.emptyList());

    when(withdrawalsValidator.validateWithdrawals(Optional.of(withdrawals))).thenReturn(true);

    assertThat(
            new MainnetBlockBodyValidator(protocolSchedule)
                .validateBodyLight(
                    blockchainSetupUtil.getProtocolContext(), block, emptyList(), NONE))
        .isTrue();
  }

  @Test
  void validationFailsIfWithdrawalsValidationFails() {
    final Block block =
        blockDataGenerator.block(
            new BlockOptions()
                .setBlockNumber(1)
                .setGasUsed(0)
                .hasTransactions(false)
                .hasOmmers(false)
                .setReceiptsRoot(BodyValidation.receiptsRoot(emptyList()))
                .setLogsBloom(LogsBloomFilter.empty())
                .setParentHash(blockchainSetupUtil.getBlockchain().getChainHeadHash())
                .setWithdrawalsRoot(BodyValidation.withdrawalsRoot(withdrawals)));
    blockchainSetupUtil.getBlockchain().appendBlock(block, Collections.emptyList());

    when(withdrawalsValidator.validateWithdrawals(Optional.empty())).thenReturn(false);

    assertThat(
            new MainnetBlockBodyValidator(protocolSchedule)
                .validateBodyLight(
                    blockchainSetupUtil.getProtocolContext(), block, emptyList(), NONE))
        .isFalse();
  }

  @Test
  void validationFailsIfWithdrawalsRootValidationFails() {
    final Block block =
        blockDataGenerator.block(
            new BlockOptions()
                .setBlockNumber(1)
                .setGasUsed(0)
                .hasTransactions(false)
                .hasOmmers(false)
                .setReceiptsRoot(BodyValidation.receiptsRoot(emptyList()))
                .setLogsBloom(LogsBloomFilter.empty())
                .setParentHash(blockchainSetupUtil.getBlockchain().getChainHeadHash())
                .setWithdrawals(Optional.of(withdrawals)));
    blockchainSetupUtil.getBlockchain().appendBlock(block, Collections.emptyList());

    when(withdrawalsValidator.validateWithdrawalsRoot(block)).thenReturn(false);

    assertThat(
            new MainnetBlockBodyValidator(protocolSchedule)
                .validateBodyLight(
                    blockchainSetupUtil.getProtocolContext(), block, emptyList(), NONE))
        .isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void noneValidationModeDoesNothing() {
    final Block block = mock(Block.class);
    final List<TransactionReceipt> receipts = mock(List.class);

    final MainnetBlockBodyValidator bodyValidator = new MainnetBlockBodyValidator(protocolSchedule);

    assertThat(
            bodyValidator.validateBody(
                blockchainSetupUtil.getProtocolContext(),
                block,
                receipts,
                Hash.ZERO,
                NONE,
                BodyValidationMode.NONE))
        .isTrue();
    verifyNoInteractions(block);
    verifyNoInteractions(receipts);
  }

  @Test
  public void lightValidationDoesNotCheckRoots() {
    final Block block = setupBlock();

    final MainnetBlockBodyValidator bodyValidator = new MainnetBlockBodyValidator(protocolSchedule);
    final MainnetBlockBodyValidator bodyValidatorSpy = spy(bodyValidator);

    assertThat(
            bodyValidatorSpy.validateBody(
                blockchainSetupUtil.getProtocolContext(),
                block,
                emptyList(),
                Hash.ZERO,
                NONE,
                BodyValidationMode.LIGHT))
        .isTrue();
    verify(bodyValidatorSpy, times(1)).validateBodyLight(any(), any(), any(), any());
    verify(bodyValidatorSpy, never()).validateBodyRoots(any(), any(), any());
  }

  @Test
  public void hashOnlyValidationChecksOnlyRoots() {
    final Block block = setupBlock();

    final MainnetBlockBodyValidator bodyValidator = new MainnetBlockBodyValidator(protocolSchedule);
    final MainnetBlockBodyValidator bodyValidatorSpy = spy(bodyValidator);

    assertThat(
            bodyValidatorSpy.validateBody(
                blockchainSetupUtil.getProtocolContext(),
                block,
                emptyList(),
                block.getHeader().getStateRoot(),
                NONE,
                BodyValidationMode.ROOT_ONLY))
        .isTrue();

    verify(bodyValidatorSpy, never()).validateBodyLight(any(), any(), any(), any());
    verify(bodyValidatorSpy, times(1)).validateBodyRoots(any(), any(), any());
  }

  @Test
  public void fullValidationChecksRootsAndContent() {
    final Block block = setupBlock();

    final MainnetBlockBodyValidator bodyValidator = new MainnetBlockBodyValidator(protocolSchedule);
    final MainnetBlockBodyValidator bodyValidatorSpy = spy(bodyValidator);

    assertThat(
            bodyValidatorSpy.validateBody(
                blockchainSetupUtil.getProtocolContext(),
                block,
                emptyList(),
                block.getHeader().getStateRoot(),
                NONE,
                BodyValidationMode.FULL))
        .isTrue();
    verify(bodyValidatorSpy, times(1)).validateBodyLight(any(), any(), any(), any());
    verify(bodyValidatorSpy, times(1)).validateBodyRoots(any(), any(), any());
  }

  private Block setupBlock() {
    Block block =
        blockDataGenerator.block(
            new BlockOptions()
                .setBlockNumber(1)
                .setGasUsed(0)
                .hasTransactions(false)
                .hasOmmers(false)
                .setReceiptsRoot(BodyValidation.receiptsRoot(emptyList()))
                .setLogsBloom(LogsBloomFilter.empty())
                .setParentHash(blockchainSetupUtil.getBlockchain().getChainHeadHash())
                .setWithdrawals(Optional.of(withdrawals)));
    blockchainSetupUtil.getBlockchain().appendBlock(block, Collections.emptyList());
    return block;
  }

  @SuppressWarnings("UnusedVariable")
  @Test
  void convertBlob() {
    try {
      // optimistically tear down a potential previous loaded trusted setup
      KZGPointEvalPrecompiledContract.tearDown();
    } catch (Throwable ignore) {
      // and ignore errors in case no trusted setup was already loaded
    }
    try {
      CKZG4844JNI.loadNativeLibrary();
      CKZG4844JNI.loadTrustedSetupFromResource(
          "/kzg-trusted-setups/mainnet.txt", BlobTestFixture.class, 0);

    } catch (Exception e) {
      fail("Failed to compute commitment", e);
    }
    byte byteValue = 0x00;
    byte[] rawMaterial = new byte[131072];
    rawMaterial[0] = byteValue++;

    Blob blob = new Blob(Bytes.wrap(rawMaterial));

    var CellsAndKzgProofs = CKZG4844JNI.computeCellsAndKzgProofs(blob.getData().toArray());

    List<KZGProof> cellProofs = extractKZGProofs(CellsAndKzgProofs.getProofs());

    assertThat(cellProofs.size()).isEqualTo(BlobProofBundle.CELL_PROOFS_PER_BLOB);
  }

  public static List<KZGProof> extractKZGProofs(final byte[] input) {
    List<KZGProof> chunks = new ArrayList<>();
    int chunkSize = Bytes48.SIZE;
    int totalChunks = input.length / chunkSize;
    for (int i = 0; i < totalChunks; i++) {
      byte[] chunk = new byte[chunkSize];
      System.arraycopy(input, i * chunkSize, chunk, 0, chunkSize);
      chunks.add(new KZGProof(Bytes48.wrap(chunk)));
    }
    return chunks;
  }
}
