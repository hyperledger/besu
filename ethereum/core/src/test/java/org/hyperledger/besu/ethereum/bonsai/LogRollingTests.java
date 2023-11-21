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

package org.hyperledger.besu.ethereum.bonsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogFactoryImpl;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogLayer;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiVerkleWorldState;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unused")
@ExtendWith(MockitoExtension.class)
class LogRollingTests {

  private BonsaiWorldStateProvider archive;

  private static final BlockHeaderTestFixture blockHeaderTestFixture = new BlockHeaderTestFixture();
  private InMemoryKeyValueStorageProvider provider;
  private KeyValueStorage accountStorage;
  private KeyValueStorage codeStorage;
  private KeyValueStorage storageStorage;
  private KeyValueStorage trieBranchStorage;
  private KeyValueStorage trieLogStorage;

  private InMemoryKeyValueStorageProvider secondProvider;
  private BonsaiWorldStateProvider secondArchive;
  private KeyValueStorage secondAccountStorage;
  private KeyValueStorage secondCodeStorage;
  private KeyValueStorage secondStorageStorage;
  private KeyValueStorage secondTrieBranchStorage;
  private KeyValueStorage secondTrieLogStorage;
  private final Blockchain blockchain = mock(Blockchain.class);

  private static final Address addressOne =
      Address.fromHexString("0x1111111111111111111111111111111111111111");

  private static final BlockHeader headerOne =
      new BlockHeader(
          Hash.ZERO,
          Hash.EMPTY_LIST_HASH,
          Address.ZERO,
          Hash.fromHexString("0x3869378cd87434ffd04c4e187312d69d1430dc62e575c4b4b061ac625b88ec08"),
          Hash.EMPTY_TRIE_HASH,
          Hash.EMPTY_LIST_HASH,
          LogsBloomFilter.builder().build(),
          Difficulty.ONE,
          1,
          0,
          0,
          0,
          Bytes.EMPTY,
          Wei.ZERO,
          Hash.ZERO,
          0,
          null,
          null, // blobGasUSed
          null,
          null,
          null,
          new MainnetBlockHeaderFunctions());
  private static final BlockHeader headerTwo =
      new BlockHeader(
          headerOne.getHash(),
          Hash.EMPTY_LIST_HASH,
          Address.ZERO,
          Hash.fromHexString("0x3e7c057b149069fadbb2bd2c752184cb5c7a9c736d27682c9e557ceda8ede10e"),
          Hash.EMPTY_TRIE_HASH,
          Hash.EMPTY_LIST_HASH,
          LogsBloomFilter.builder().build(),
          Difficulty.ONE,
          2,
          0,
          0,
          0,
          Bytes.EMPTY,
          Wei.ZERO,
          Hash.ZERO,
          0,
          null,
          null, // blobGasUsed
          null,
          null,
          null,
          new MainnetBlockHeaderFunctions());
  private static final BlockHeader headerThree =
      new BlockHeader(
          headerOne.getHash(),
          Hash.EMPTY_LIST_HASH,
          Address.ZERO,
          Hash.fromHexString("0xec5d7bd6bd7ce01e58bb389475767350852e2ce2bb72b8cd9c9b55d118c14e07"),
          Hash.EMPTY_TRIE_HASH,
          Hash.EMPTY_LIST_HASH,
          LogsBloomFilter.builder().build(),
          Difficulty.ONE,
          3,
          0,
          0,
          0,
          Bytes.EMPTY,
          Wei.ZERO,
          Hash.ZERO,
          0,
          null,
          null, // blobGasUsed
          null,
          null,
          null,
          new MainnetBlockHeaderFunctions());

  private static final BlockHeader headerFour =
      new BlockHeader(
          headerOne.getHash(),
          Hash.EMPTY_LIST_HASH,
          Address.ZERO,
          Hash.fromHexString("0x3869378cd87434ffd04c4e187312d69d1430dc62e575c4b4b061ac625b88ec08"),
          Hash.EMPTY_TRIE_HASH,
          Hash.EMPTY_LIST_HASH,
          LogsBloomFilter.builder().build(),
          Difficulty.ONE,
          3,
          0,
          0,
          0,
          Bytes.EMPTY,
          Wei.ZERO,
          Hash.ZERO,
          0,
          null,
          null, // blobGasUsed
          null,
          null,
          null,
          new MainnetBlockHeaderFunctions());

  @BeforeEach
  void createStorage() {
    provider = new InMemoryKeyValueStorageProvider();
    archive = InMemoryKeyValueStorageProvider.createBonsaiInMemoryWorldStateArchive(blockchain);
    accountStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    codeStorage = provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.CODE_STORAGE);
    storageStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
    trieBranchStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE);
    trieLogStorage =
        provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);

    secondProvider = new InMemoryKeyValueStorageProvider();
    secondArchive =
        InMemoryKeyValueStorageProvider.createBonsaiInMemoryWorldStateArchive(blockchain);
    secondAccountStorage =
        secondProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE);
    secondCodeStorage =
        secondProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.CODE_STORAGE);
    secondStorageStorage =
        secondProvider.getStorageBySegmentIdentifier(
            KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE);
    secondTrieBranchStorage =
        secondProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE);
    secondTrieLogStorage =
        secondProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
  }

  @Test
  void rollForwardComparedWithTestnet() { // TODO change the name

    final BonsaiVerkleWorldState worldState =
        new BonsaiVerkleWorldState(
            archive,
            new BonsaiWorldStateKeyValueStorage(provider, new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT);
    final WorldUpdater updater = worldState.updater();

    final MutableAccount contract =
        updater.createAccount(
            Address.fromHexString("0x2a97e18168654393a573599759104efdfec6d8bd"), 1, Wei.ZERO);
    contract.setCode(
        Bytes.fromHexString(
            "608060405234801561000f575f80fd5b5060043610610034575f3560e01c80632e64cec1146100385780636057361d14610056575b5f80fd5b610040610072565b60405161004d919061029a565b60405180910390f35b610070600480360381019061006b91906102e1565b61019c565b005b5f8060405161008090610275565b604051809103905ff080158015610099573d5f803e3d5ffd5b5090505f60015f9054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16632e64cec16040518163ffffffff1660e01b8152600401602060405180830381865afa158015610107573d5f803e3d5ffd5b505050506040513d601f19601f8201168201806040525081019061012b9190610320565b90508173ffffffffffffffffffffffffffffffffffffffff16636057361d826040518263ffffffff1660e01b8152600401610166919061029a565b5f604051808303815f87803b15801561017d575f80fd5b505af115801561018f573d5f803e3d5ffd5b505050505f549250505090565b805f819055505f6040516101af90610275565b604051809103905ff0801580156101c8573d5f803e3d5ffd5b5090508073ffffffffffffffffffffffffffffffffffffffff16636057361d836040518263ffffffff1660e01b8152600401610204919061029a565b5f604051808303815f87803b15801561021b575f80fd5b505af115801561022d573d5f803e3d5ffd5b505050508060015f6101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055505050565b6101e38061034c83390190565b5f819050919050565b61029481610282565b82525050565b5f6020820190506102ad5f83018461028b565b92915050565b5f80fd5b6102c081610282565b81146102ca575f80fd5b50565b5f813590506102db816102b7565b92915050565b5f602082840312156102f6576102f56102b3565b5b5f610303848285016102cd565b91505092915050565b5f8151905061031a816102b7565b92915050565b5f60208284031215610335576103346102b3565b5b5f6103428482850161030c565b9150509291505056fe608060405234801561000f575f80fd5b506101c68061001d5f395ff3fe60806040526004361061003e575f3560e01c80632711432d146100425780632e64cec11461006c5780636057361d14610096578063d64c8ca4146100be575b5f80fd5b34801561004d575f80fd5b506100566100c8565b604051610063919061011e565b60405180910390f35b348015610077575f80fd5b506100806100d1565b60405161008d919061011e565b60405180910390f35b3480156100a1575f80fd5b506100bc60048036038101906100b79190610165565b6100d9565b005b6100c66100e9565b005b5f600154905090565b5f8054905090565b805f819055508060018190555050565b5f3390508073ffffffffffffffffffffffffffffffffffffffff16ff5b5f819050919050565b61011881610106565b82525050565b5f6020820190506101315f83018461010f565b92915050565b5f80fd5b61014481610106565b811461014e575f80fd5b50565b5f8135905061015f8161013b565b92915050565b5f6020828403121561017a57610179610137565b5b5f61018784828501610151565b9150509291505056fea2646970667358221220dc349a9524617af5742ac60346440c0d09b175e4d9c4d95e378a9652cb9acbb064736f6c63430008160033a264697066735822122079744fe4f745783dffcec2415a6b99b8b7b340bcf4a768d5563f00d2ec1f916b64736f6c63430008160033"));
    contract.setStorageValue(UInt256.ZERO, UInt256.fromHexString("0x0c"));
    final MutableAccount mutableAccount =
        updater.createAccount(
            Address.fromHexString("0xb247faa497c752519917402cd79414727222f792"),
            2,
            Wei.fromHexString("56cdce8421269edc4"));
    updater.commit();
    worldState.persist(null);
  }

  @Test
  void simpleRollForwardTest() {

    final BonsaiVerkleWorldState worldState =
        new BonsaiVerkleWorldState(
            archive,
            new BonsaiWorldStateKeyValueStorage(provider, new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT);
    final WorldUpdater updater = worldState.updater();

    final MutableAccount mutableAccount = updater.createAccount(addressOne, 1, Wei.of(1L));
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();
    worldState.persist(headerOne);

    final BonsaiVerkleWorldState secondWorldState =
        new BonsaiVerkleWorldState(
            secondArchive,
            new BonsaiWorldStateKeyValueStorage(secondProvider, new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT);
    final BonsaiWorldStateUpdateAccumulator secondUpdater =
        (BonsaiWorldStateUpdateAccumulator) secondWorldState.updater();

    final Optional<byte[]> value = trieLogStorage.get(headerOne.getHash().toArrayUnsafe());

    final TrieLogLayer layer =
        TrieLogFactoryImpl.readFrom(new BytesValueRLPInput(Bytes.wrap(value.get()), false));

    secondUpdater.rollForward(layer);
    secondUpdater.commit();
    secondWorldState.persist(null);

    assertKeyValueStorageEqual(accountStorage, secondAccountStorage);
    assertKeyValueStorageEqual(codeStorage, secondCodeStorage);
    assertKeyValueStorageEqual(storageStorage, secondStorageStorage);
    final KeyValueStorageTransaction tx = trieBranchStorage.startTransaction();
    tx.remove(BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY);
    tx.commit();
    assertKeyValueStorageEqual(trieBranchStorage, secondTrieBranchStorage);
    // trie logs won't be the same, we shouldn't generate logs on rolls.
    assertKeyValueSubset(trieLogStorage, secondTrieLogStorage);
    assertThat(secondWorldState.rootHash()).isEqualByComparingTo(worldState.rootHash());
  }

  @Test
  void rollForwardTwice() {
    final BonsaiVerkleWorldState worldState =
        new BonsaiVerkleWorldState(
            archive,
            new BonsaiWorldStateKeyValueStorage(provider, new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT);

    final WorldUpdater updater = worldState.updater();
    final MutableAccount mutableAccount = updater.createAccount(addressOne, 1, Wei.of(1L));
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();

    worldState.persist(headerOne);

    final WorldUpdater updater2 = worldState.updater();
    final MutableAccount mutableAccount2 = updater2.getAccount(addressOne);
    mutableAccount2.setStorageValue(UInt256.ONE, UInt256.valueOf(2));
    updater2.commit();

    worldState.persist(headerTwo);

    final BonsaiVerkleWorldState secondWorldState =
        new BonsaiVerkleWorldState(
            secondArchive,
            new BonsaiWorldStateKeyValueStorage(secondProvider, new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT);
    final BonsaiWorldStateUpdateAccumulator secondUpdater =
        (BonsaiWorldStateUpdateAccumulator) secondWorldState.updater();

    final TrieLogLayer layerOne = getTrieLogLayer(trieLogStorage, headerOne.getHash());
    secondUpdater.rollForward(layerOne);
    secondUpdater.commit();
    secondWorldState.persist(null);

    final TrieLogLayer layerTwo = getTrieLogLayer(trieLogStorage, headerTwo.getHash());
    secondUpdater.rollForward(layerTwo);
    secondUpdater.commit();
    secondWorldState.persist(null);

    assertKeyValueStorageEqual(accountStorage, secondAccountStorage);
    assertKeyValueStorageEqual(codeStorage, secondCodeStorage);
    assertKeyValueStorageEqual(storageStorage, secondStorageStorage);
    final KeyValueStorageTransaction tx = trieBranchStorage.startTransaction();
    tx.remove(BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY);
    tx.commit();
    assertKeyValueStorageEqual(trieBranchStorage, secondTrieBranchStorage);
    // trie logs won't be the same, we shouldn't generate logs on rolls.
    assertKeyValueSubset(trieLogStorage, secondTrieLogStorage);
    assertThat(secondWorldState.rootHash()).isEqualByComparingTo(worldState.rootHash());
  }

  @Test
  void rollBackOnce() {
    final BonsaiVerkleWorldState worldState =
        new BonsaiVerkleWorldState(
            archive,
            new BonsaiWorldStateKeyValueStorage(provider, new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT);

    final WorldUpdater updater = worldState.updater();
    final MutableAccount mutableAccount = updater.createAccount(addressOne, 1, Wei.of(1L));
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();

    worldState.persist(headerOne);

    final WorldUpdater updater2 = worldState.updater();
    final MutableAccount mutableAccount2 = updater2.getAccount(addressOne);
    mutableAccount2.setStorageValue(UInt256.ONE, UInt256.valueOf(2));
    updater2.commit();

    worldState.persist(headerTwo);
    final BonsaiWorldStateUpdateAccumulator firstRollbackUpdater =
        (BonsaiWorldStateUpdateAccumulator) worldState.updater();

    final TrieLogLayer layerTwo = getTrieLogLayer(trieLogStorage, headerTwo.getHash());
    firstRollbackUpdater.rollBack(layerTwo);

    worldState.persist(headerOne);

    final BonsaiVerkleWorldState secondWorldState =
        new BonsaiVerkleWorldState(
            secondArchive,
            new BonsaiWorldStateKeyValueStorage(secondProvider, new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT);

    final WorldUpdater secondUpdater = secondWorldState.updater();
    final MutableAccount secondMutableAccount =
        secondUpdater.createAccount(addressOne, 1, Wei.of(1L));
    secondMutableAccount.setCode(Bytes.of(0, 1, 2));
    secondMutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    secondUpdater.commit();

    secondWorldState.persist(null);

    assertKeyValueStorageEqual(accountStorage, secondAccountStorage);
    assertKeyValueStorageEqual(codeStorage, secondCodeStorage);
    assertKeyValueStorageEqual(storageStorage, secondStorageStorage);
    final KeyValueStorageTransaction tx = trieBranchStorage.startTransaction();
    tx.remove(BonsaiWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY);
    tx.commit();
    assertKeyValueStorageEqual(trieBranchStorage, secondTrieBranchStorage);
    // trie logs won't be the same, we don't delete the roll back log
    assertKeyValueSubset(trieLogStorage, secondTrieLogStorage);
    assertThat(secondWorldState.rootHash()).isEqualByComparingTo(worldState.rootHash());
  }

  @Test
  void rollBackTwice() {
    final BonsaiVerkleWorldState worldState =
        new BonsaiVerkleWorldState(
            archive,
            new BonsaiWorldStateKeyValueStorage(provider, new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT);

    final WorldUpdater updater = worldState.updater();
    final MutableAccount mutableAccount = updater.createAccount(addressOne, 1, Wei.of(1L));
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();

    worldState.persist(headerOne);
    final TrieLogLayer layerOne = getTrieLogLayer(trieLogStorage, headerOne.getHash());

    final WorldUpdater updater2 = worldState.updater();
    final MutableAccount mutableAccount2 = updater2.getAccount(addressOne);
    mutableAccount2.setStorageValue(UInt256.ONE, UInt256.valueOf(2));
    updater2.commit();

    worldState.persist(headerTwo);
    final BonsaiWorldStateUpdateAccumulator firstRollbackUpdater =
        (BonsaiWorldStateUpdateAccumulator) worldState.updater();

    final TrieLogLayer layerTwo = getTrieLogLayer(trieLogStorage, headerTwo.getHash());
    firstRollbackUpdater.rollBack(layerTwo);

    worldState.persist(headerOne);

    final BonsaiWorldStateUpdateAccumulator secondRollbackUpdater =
        (BonsaiWorldStateUpdateAccumulator) worldState.updater();
    secondRollbackUpdater.rollBack(layerOne);

    worldState.persist(null);

    assertThat(worldState.rootHash()).isEqualTo(Bytes32.ZERO);
  }

  @Test
  void rollBackFourTimes() {
    final BonsaiVerkleWorldState worldState =
        new BonsaiVerkleWorldState(
            archive,
            new BonsaiWorldStateKeyValueStorage(provider, new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT);

    final WorldUpdater updater = worldState.updater();
    final MutableAccount mutableAccount = updater.createAccount(addressOne, 1, Wei.of(1L));
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();

    worldState.persist(headerOne);
    final TrieLogLayer layerOne = getTrieLogLayer(trieLogStorage, headerOne.getHash());

    final WorldUpdater updater2 = worldState.updater();
    final MutableAccount mutableAccount2 = updater2.getAccount(addressOne);
    mutableAccount2.setStorageValue(UInt256.ONE, UInt256.valueOf(2));
    updater2.commit();

    worldState.persist(headerTwo);
    final TrieLogLayer layerTwo = getTrieLogLayer(trieLogStorage, headerTwo.getHash());

    final WorldUpdater updater3 = worldState.updater();
    final MutableAccount mutableAccount3 = updater3.getAccount(addressOne);
    mutableAccount3.setStorageValue(UInt256.ONE, UInt256.valueOf(0));
    updater3.commit();

    worldState.persist(headerThree);
    final TrieLogLayer layerThree = getTrieLogLayer(trieLogStorage, headerThree.getHash());

    final WorldUpdater updater4 = worldState.updater();
    final MutableAccount mutableAccount4 = updater4.getAccount(addressOne);
    mutableAccount4.setStorageValue(UInt256.ONE, UInt256.valueOf(1));
    updater4.commit();

    worldState.persist(headerFour);
    final TrieLogLayer layerFour = getTrieLogLayer(trieLogStorage, headerFour.getHash());

    final BonsaiWorldStateUpdateAccumulator firstRollbackUpdater =
        (BonsaiWorldStateUpdateAccumulator) worldState.updater();

    firstRollbackUpdater.rollBack(layerFour);

    System.out.println(layerFour.dump());
    worldState.persist(headerThree);

    final BonsaiWorldStateUpdateAccumulator secondRollbackUpdater =
        (BonsaiWorldStateUpdateAccumulator) worldState.updater();
    secondRollbackUpdater.rollBack(layerThree);

    worldState.persist(headerTwo);

    final BonsaiWorldStateUpdateAccumulator thirdRollbackUpdater =
        (BonsaiWorldStateUpdateAccumulator) worldState.updater();
    thirdRollbackUpdater.rollBack(layerTwo);

    worldState.persist(headerOne);

    final BonsaiWorldStateUpdateAccumulator fourRollbackUpdater =
        (BonsaiWorldStateUpdateAccumulator) worldState.updater();
    fourRollbackUpdater.rollBack(layerOne);

    worldState.persist(null);

    assertThat(worldState.rootHash()).isEqualTo(Bytes32.ZERO);
  }

  @Test
  void rollingWithRemovedStorageValue() {
    final BonsaiVerkleWorldState worldState =
        new BonsaiVerkleWorldState(
            archive,
            new BonsaiWorldStateKeyValueStorage(provider, new NoOpMetricsSystem()),
            EvmConfiguration.DEFAULT);

    final WorldUpdater updater = worldState.updater();
    final MutableAccount mutableAccount = updater.createAccount(addressOne, 1, Wei.of(1L));
    mutableAccount.setCode(Bytes.of(0, 1, 2));
    mutableAccount.setStorageValue(UInt256.ONE, UInt256.ONE);
    updater.commit();

    worldState.persist(headerOne);

    /*final WorldUpdater updater2 = worldState.updater();
    final MutableAccount mutableAccount2 = updater2.getAccount(addressOne);
    mutableAccount2.setStorageValue(UInt256.ONE, UInt256.ZERO);
    updater2.commit();

    blockHeaderTestFixture.stateRoot(Hash.fromHexString("0x1879f69465e8ef937ce1f13cb5b328437239a2764982cea5e337cd5d217a2866"));
    blockHeaderTestFixture.number(2);
    final BlockHeader blockHeaderTwo = blockHeaderTestFixture.buildHeader();
    worldState.persist(blockHeaderTwo);

    final BonsaiWorldStateUpdateAccumulator firstRollbackUpdater =
            (BonsaiWorldStateUpdateAccumulator) worldState.updater();

    final TrieLogLayer layerTwo = getTrieLogLayer(trieLogStorage, blockHeaderTwo.getBlockHash());
    firstRollbackUpdater.rollBack(layerTwo);
    System.out.println("rollback");

    worldState.persist(null);
    assertThat(worldState.rootHash()).isEqualTo(blockHeaderOne.getStateRoot());*/

    final BonsaiWorldStateUpdateAccumulator secondRollbackUpdater =
        (BonsaiWorldStateUpdateAccumulator) worldState.updater();

    final TrieLogLayer layerOne = getTrieLogLayer(trieLogStorage, headerOne.getBlockHash());
    secondRollbackUpdater.rollBack(layerOne);

    worldState.persist(null);
    assertThat(worldState.rootHash()).isEqualTo(Bytes32.ZERO);
  }

  private TrieLogLayer getTrieLogLayer(final KeyValueStorage storage, final Bytes key) {
    return storage
        .get(key.toArrayUnsafe())
        .map(bytes -> TrieLogFactoryImpl.readFrom(new BytesValueRLPInput(Bytes.wrap(bytes), false)))
        .get();
  }

  private static void assertKeyValueStorageEqual(
      final KeyValueStorage first, final KeyValueStorage second) {
    final var firstKeys =
        first.getAllKeysThat(k -> true).stream().map(Bytes::wrap).collect(Collectors.toSet());
    final var secondKeys =
        second.getAllKeysThat(k -> true).stream().map(Bytes::wrap).collect(Collectors.toSet());

    assertThat(secondKeys).isEqualTo(firstKeys);
    for (final Bytes key : firstKeys) {
      assertThat(Bytes.wrap(second.get(key.toArrayUnsafe()).get()))
          .isEqualByComparingTo(Bytes.wrap(first.get(key.toArrayUnsafe()).get()));
    }
  }

  private static void assertKeyValueSubset(
      final KeyValueStorage largerSet, final KeyValueStorage smallerSet) {
    final var largerKeys =
        largerSet.getAllKeysThat(k -> true).stream().map(Bytes::wrap).collect(Collectors.toSet());
    final var smallerKeys =
        smallerSet.getAllKeysThat(k -> true).stream().map(Bytes::wrap).collect(Collectors.toSet());

    assertThat(largerKeys).containsAll(smallerKeys);
    for (final Bytes key : largerKeys) {
      if (smallerKeys.contains(key)) {
        assertThat(Bytes.wrap(largerSet.get(key.toArrayUnsafe()).get()))
            .isEqualByComparingTo(Bytes.wrap(smallerSet.get(key.toArrayUnsafe()).get()));
      }
    }
  }
}
