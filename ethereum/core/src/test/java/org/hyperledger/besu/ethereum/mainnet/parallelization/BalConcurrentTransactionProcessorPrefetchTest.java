package org.hyperledger.besu.ethereum.mainnet.parallelization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.WorldStateConfig.createStatefulConfigWithTrie;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.ImmutableBalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.CodeCache;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.NoOpBonsaiCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.NoopBonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiCachedWorldStateStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.NoOpTrieLogManager;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class BalConcurrentTransactionProcessorPrefetchTest {

  private BonsaiWorldStateKeyValueStorage parentStorage;
  private BonsaiCachedWorldStateStorage cachedStorage;
  private ProtocolContext protocolContext;
  private BlockHeader blockHeader;
  private BonsaiWorldState worldState;

  @BeforeEach
  public void setup() {
    parentStorage = new BonsaiWorldStateKeyValueStorage(
            new InMemoryKeyValueStorageProvider(),
            new NoOpMetricsSystem(),
            DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);

    cachedStorage = new BonsaiCachedWorldStateStorage(
            parentStorage, 1000, 1000, 1000, 1000);

    worldState = new BonsaiWorldState(
            cachedStorage,
            new NoopBonsaiCachedMerkleTrieLoader(),
            new NoOpBonsaiCachedWorldStorageManager(
                    cachedStorage, EvmConfiguration.DEFAULT, new CodeCache()),
            new NoOpTrieLogManager(),
            EvmConfiguration.DEFAULT,
            createStatefulConfigWithTrie(),
            new CodeCache());

    protocolContext = mock(ProtocolContext.class);
    MutableBlockchain blockchain = mock(MutableBlockchain.class);
    BlockHeader chainHeadBlockHeader = mock(BlockHeader.class);
    WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);

    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHeadHeader()).thenReturn(chainHeadBlockHeader);
    when(chainHeadBlockHeader.getHash()).thenReturn(Hash.ZERO);
    when(chainHeadBlockHeader.getStateRoot()).thenReturn(Hash.EMPTY_TRIE_HASH);

    blockHeader = mock(BlockHeader.class);
    when(blockHeader.getParentHash()).thenReturn(Hash.ZERO);

    when(protocolContext.getWorldStateArchive()).thenReturn(worldStateArchive);
    when(worldStateArchive.getWorldState(any())).thenReturn(Optional.of(worldState));
  }

  @Test
  public void testPrefetch_loadsAccountsIntoCache() throws Exception {
    Address address1 = Address.fromHexString("0x1111111111111111111111111111111111111111");
    Address address2 = Address.fromHexString("0x2222222222222222222222222222222222222222");
    
    Bytes accountData1 = Bytes.of(1, 2, 3);
    Bytes accountData2 = Bytes.of(4, 5, 6);

    BonsaiWorldStateKeyValueStorage.Updater updater = parentStorage.updater();
    updater.putAccountInfoState(address1.addressHash(), accountData1);
    updater.putAccountInfoState(address2.addressHash(), accountData2);
    updater.commit();

    List<BlockAccessList.AccountChanges> accountChangesList = new ArrayList<>();
    accountChangesList.add(new BlockAccessList.AccountChanges(
            address1, List.of(), List.of(), List.of(), List.of(), List.of()));
    accountChangesList.add(new BlockAccessList.AccountChanges(
            address2, List.of(), List.of(), List.of(), List.of(), List.of()));

    BlockAccessList blockAccessList = new BlockAccessList(accountChangesList);
    
    BalConfiguration config = ImmutableBalConfiguration.builder()
            .balProcessingTimeout(Duration.ofSeconds(10))
            .build();
    
    MainnetTransactionProcessor txProcessor = mock(MainnetTransactionProcessor.class);
    BalConcurrentTransactionProcessor processor = new BalConcurrentTransactionProcessor(
            txProcessor, blockAccessList, config);

    assertThat(cachedStorage.getCacheSize(ACCOUNT_INFO_STATE)).isZero();

    processor.preFetchRead(protocolContext, blockHeader, Runnable::run);

    assertThat(cachedStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(2);
    assertThat(cachedStorage.isCached(ACCOUNT_INFO_STATE, address1.addressHash())).isTrue();
    assertThat(cachedStorage.isCached(ACCOUNT_INFO_STATE, address2.addressHash())).isTrue();
  }

  @Test
  public void testPrefetch_loadsStorageSlotsIntoCache() throws Exception {
    Address address = Address.fromHexString("0x1111111111111111111111111111111111111111");
    StorageSlotKey slot1 = new StorageSlotKey(UInt256.valueOf(1));
    StorageSlotKey slot2 = new StorageSlotKey(UInt256.valueOf(2));
    
    Bytes accountData = Bytes.of(1, 2, 3);
    Bytes storageValue1 = Bytes.of(10);
    Bytes storageValue2 = Bytes.of(20);

    BonsaiWorldStateKeyValueStorage.Updater updater = parentStorage.updater();
    updater.putAccountInfoState(address.addressHash(), accountData);
    updater.putStorageValueBySlotHash(address.addressHash(), slot1.getSlotHash(), storageValue1);
    updater.putStorageValueBySlotHash(address.addressHash(), slot2.getSlotHash(), storageValue2);
    updater.commit();

    List<BlockAccessList.SlotChanges> storageChangesList = new ArrayList<>();
    storageChangesList.add(new BlockAccessList.SlotChanges(slot1, List.of()));
    storageChangesList.add(new BlockAccessList.SlotChanges(slot2, List.of()));

    List<BlockAccessList.AccountChanges> accountChangesList = new ArrayList<>();
    accountChangesList.add(new BlockAccessList.AccountChanges(
            address, storageChangesList, List.of(), List.of(), List.of(), List.of()));

    BlockAccessList blockAccessList = new BlockAccessList(accountChangesList);
    
    BalConfiguration config = ImmutableBalConfiguration.builder()
            .balProcessingTimeout(Duration.ofSeconds(10))
            .build();
    
    MainnetTransactionProcessor txProcessor = mock(MainnetTransactionProcessor.class);
    BalConcurrentTransactionProcessor processor = new BalConcurrentTransactionProcessor(
            txProcessor, blockAccessList, config);

    assertThat(cachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE)).isZero();

    processor.preFetchRead(protocolContext, blockHeader, Runnable::run);

    assertThat(cachedStorage.isCached(ACCOUNT_INFO_STATE, address.addressHash())).isTrue();
    assertThat(cachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE)).isEqualTo(2);
    assertThat(cachedStorage.isCached(ACCOUNT_STORAGE_STORAGE, Bytes.concatenate(address.addressHash(),slot1.getSlotHash()))).isTrue();
    assertThat(cachedStorage.isCached(ACCOUNT_STORAGE_STORAGE, Bytes.concatenate(address.addressHash(),slot2.getSlotHash()))).isTrue();
  }

  @Test
  public void testPrefetch_handlesEmptyAccessList() throws Exception {
    BlockAccessList blockAccessList = new BlockAccessList(List.of());
    
    BalConfiguration config = ImmutableBalConfiguration.builder()
            .balProcessingTimeout(Duration.ofSeconds(10))
            .build();
    
    MainnetTransactionProcessor txProcessor = mock(MainnetTransactionProcessor.class);
    BalConcurrentTransactionProcessor processor = new BalConcurrentTransactionProcessor(
            txProcessor, blockAccessList, config);

    processor.preFetchRead(protocolContext, blockHeader, Runnable::run);

    assertThat(cachedStorage.getCacheSize(ACCOUNT_INFO_STATE)).isZero();
    assertThat(cachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE)).isZero();
  }

  @Test
  public void testPrefetch_multipleAccountsAndSlots() throws Exception {
    List<BlockAccessList.AccountChanges> allChanges = new ArrayList<>();

    for (int i = 0; i < 5; i++) {
      Address address = Address.fromHexString(String.format("0x%040d", i + 1));
      Bytes accountData = Bytes.of(i);
      
      BonsaiWorldStateKeyValueStorage.Updater accountUpdater = parentStorage.updater();
      accountUpdater.putAccountInfoState(address.addressHash(), accountData);
      accountUpdater.commit();
      
      List<BlockAccessList.SlotChanges> storageChangesList = new ArrayList<>();
      for (int j = 0; j < 3; j++) {
        StorageSlotKey slot = new StorageSlotKey(UInt256.valueOf(i * 10 + j));
        Bytes storageValue = Bytes.of(i * 10 + j);
        
        BonsaiWorldStateKeyValueStorage.Updater storageUpdater = parentStorage.updater();
        storageUpdater.putStorageValueBySlotHash(address.addressHash(), slot.getSlotHash(), storageValue);
        storageUpdater.commit();
        
        storageChangesList.add(new BlockAccessList.SlotChanges(slot, List.of()));
      }
      
      allChanges.add(new BlockAccessList.AccountChanges(
              address, storageChangesList, List.of(), List.of(), List.of(), List.of()));
    }

    BlockAccessList blockAccessList = new BlockAccessList(allChanges);
    
    BalConfiguration config = ImmutableBalConfiguration.builder()
            .balProcessingTimeout(Duration.ofSeconds(10))
            .build();
    
    MainnetTransactionProcessor txProcessor = mock(MainnetTransactionProcessor.class);
    BalConcurrentTransactionProcessor processor = new BalConcurrentTransactionProcessor(
            txProcessor, blockAccessList, config);

    processor.preFetchRead(protocolContext, blockHeader, Runnable::run);

    assertThat(cachedStorage.getCacheSize(ACCOUNT_INFO_STATE)).isEqualTo(5);
    assertThat(cachedStorage.getCacheSize(ACCOUNT_STORAGE_STORAGE)).isEqualTo(15);
  }

}