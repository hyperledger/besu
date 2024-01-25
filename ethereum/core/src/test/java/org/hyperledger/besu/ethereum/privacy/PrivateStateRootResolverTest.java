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
package org.hyperledger.besu.ethereum.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PrivateStateRootResolverTest {

  private static final BlockDataGenerator BLOCK_GENERATOR = new BlockDataGenerator();
  private static MutableBlockchain BLOCKCHAIN;

  private static final Hash pmt1StateHash =
      Hash.fromHexString("0x37659019840d6e04e740614d1ad93d62f0d9d7cc423b2178189f391db602a6a6");
  private static final Hash pmt2StateHash =
      Hash.fromHexString("0x12d390c87b405e91523b5829002bf90095005366eb9aa168ff8a18540902e410");
  private static final Bytes32 privacyGroupId =
      Bytes32.wrap(Bytes.fromBase64String("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="));
  private static final Bytes32 failingPrivacyGroupId =
      Bytes32.wrap(Bytes.fromBase64String("Ko2bVqD+nNlNYL5EE7y3IdOnviftjiizpjRt+HTuFBs="));

  private PrivateStateStorage privateStateStorage;

  @BeforeAll
  public static void setupClass() {
    BLOCKCHAIN =
        InMemoryKeyValueStorageProvider.createInMemoryBlockchain(BLOCK_GENERATOR.genesisBlock());
    for (int i = 1; i <= 69; i++) {
      final BlockDataGenerator.BlockOptions options =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(BLOCKCHAIN.getBlockHashByNumber(i - 1).get());
      final Block block = BLOCK_GENERATOR.block(options);
      final List<TransactionReceipt> receipts = BLOCK_GENERATOR.receipts(block);
      BLOCKCHAIN.appendBlock(block, receipts);
    }
  }

  @BeforeEach
  public void setUp() {
    privateStateStorage = InMemoryKeyValueStorageProvider.createInMemoryPrivateStateStorage();
  }

  @Test
  public void mustResolveEmptyStateRootWhenChainHeadIsNotCommitted() {
    final BlockDataGenerator.BlockOptions options =
        new BlockDataGenerator.BlockOptions()
            .setBlockNumber(BLOCKCHAIN.getChainHeadBlockNumber())
            .setParentHash(BLOCKCHAIN.getChainHeadHash());
    final Block block = BLOCK_GENERATOR.block(options);
    final PrivateStateRootResolver privateStateRootResolver =
        new PrivateStateRootResolver(privateStateStorage);
    assertThat(
            privateStateRootResolver.resolveLastStateRoot(
                privacyGroupId, block.getHeader().getHash()))
        .isEqualTo(PrivateStateRootResolver.EMPTY_ROOT_HASH);
  }

  @Test
  public void resolveEmptyRootHashWhenNoCommitmentForPrivacyGroupExists() {
    final PrivateStateRootResolver privateStateRootResolver =
        new PrivateStateRootResolver(privateStateStorage);
    assertThat(
            privateStateRootResolver.resolveLastStateRoot(
                privacyGroupId, BLOCKCHAIN.getChainHeadHeader().getHash()))
        .isEqualTo(PrivateStateRootResolver.EMPTY_ROOT_HASH);
  }

  @Test
  public void resolveExpectedRootHashWhenCommitmentForPrivacyGroupExists() {
    final PrivateStateStorage.Updater updater = privateStateStorage.updater();
    updater.putPrivateBlockMetadata(
        BLOCKCHAIN.getBlockByNumber(16).get().getHash(),
        Bytes32.wrap(privacyGroupId),
        new PrivateBlockMetadata(
            Collections.singletonList(
                new PrivateTransactionMetadata(
                    BLOCK_GENERATOR.transaction().getHash(), pmt1StateHash))));
    updater.putPrivacyGroupHeadBlockMap(
        BLOCKCHAIN.getChainHeadHash(),
        new PrivacyGroupHeadBlockMap(
            Collections.singletonMap(
                Bytes32.wrap(privacyGroupId), BLOCKCHAIN.getBlockByNumber(16).get().getHash())));
    updater.commit();
    final PrivateStateRootResolver privateStateRootResolver =
        new PrivateStateRootResolver(privateStateStorage);
    assertThat(
            privateStateRootResolver.resolveLastStateRoot(
                privacyGroupId, BLOCKCHAIN.getChainHeadHash()))
        .isEqualTo(pmt1StateHash);
  }

  @Test
  public void resolveCorrectRootHashWhenMultipleCommitmentsExistForPrivacyGroup() {
    final PrivateStateStorage.Updater updater = privateStateStorage.updater();
    updater.putPrivateBlockMetadata(
        BLOCKCHAIN.getBlockByNumber(16).get().getHash(),
        Bytes32.wrap(privacyGroupId),
        new PrivateBlockMetadata(
            Collections.singletonList(
                new PrivateTransactionMetadata(
                    BLOCK_GENERATOR.transaction().getHash(), pmt1StateHash))));
    updater.putPrivateBlockMetadata(
        BLOCKCHAIN.getBlockByNumber(16).get().getHash(),
        Bytes32.wrap(failingPrivacyGroupId),
        new PrivateBlockMetadata(
            Collections.singletonList(
                new PrivateTransactionMetadata(
                    BLOCK_GENERATOR.transaction().getHash(), pmt2StateHash))));
    updater.putPrivacyGroupHeadBlockMap(
        BLOCKCHAIN.getChainHeadHash(),
        new PrivacyGroupHeadBlockMap(
            Collections.singletonMap(
                Bytes32.wrap(privacyGroupId), BLOCKCHAIN.getBlockByNumber(16).get().getHash())));
    updater.commit();
    final PrivateStateRootResolver privateStateRootResolver =
        new PrivateStateRootResolver(privateStateStorage);
    assertThat(
            privateStateRootResolver.resolveLastStateRoot(
                privacyGroupId, BLOCKCHAIN.getChainHeadHash()))
        .isEqualTo(pmt1StateHash);
  }

  @Test
  public void resolveLatestRootHashWhenMultipleCommitmentsForTheSamePrivacyGroupExist() {
    final PrivateStateStorage.Updater updater = privateStateStorage.updater();
    updater.putPrivateBlockMetadata(
        BLOCKCHAIN.getBlockByNumber(16).get().getHash(),
        Bytes32.wrap(privacyGroupId),
        new PrivateBlockMetadata(
            Arrays.asList(
                new PrivateTransactionMetadata(
                    BLOCK_GENERATOR.transaction().getHash(), pmt1StateHash),
                new PrivateTransactionMetadata(
                    BLOCK_GENERATOR.transaction().getHash(), pmt2StateHash))));
    updater.putPrivacyGroupHeadBlockMap(
        BLOCKCHAIN.getChainHeadHash(),
        new PrivacyGroupHeadBlockMap(
            Collections.singletonMap(
                Bytes32.wrap(privacyGroupId), BLOCKCHAIN.getBlockByNumber(16).get().getHash())));
    updater.commit();
    final PrivateStateRootResolver privateStateRootResolver =
        new PrivateStateRootResolver(privateStateStorage);
    assertThat(
            privateStateRootResolver.resolveLastStateRoot(
                privacyGroupId, BLOCKCHAIN.getChainHeadHash()))
        .isEqualTo(pmt2StateHash);
  }
}
