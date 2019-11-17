package org.hyperledger.besu;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.controller.GasLimitCalculator;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.types.SendRequest;
import org.hyperledger.besu.enclave.types.SendRequestBesu;
import org.hyperledger.besu.enclave.types.SendRequestLegacy;
import org.hyperledger.besu.enclave.types.SendResponse;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.InMemoryStorageProvider;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.MiningParametersTestBuilder;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.PrivateTransaction;
import org.hyperledger.besu.ethereum.privacy.Restriction;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValuePrivacyStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.orion.testutil.OrionKeyConfiguration;
import org.hyperledger.orion.testutil.OrionTestHarness;
import org.hyperledger.orion.testutil.OrionTestHarnessFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivacyReorgTest {
  private static final int MAX_OPEN_FILES = 1024;
  private static final long CACHE_CAPACITY = 8388608;
  private static final int MAX_BACKGROUND_COMPACTIONS = 4;
  private static final int BACKGROUND_THREAD_COUNT = 4;
  private static final SECP256K1.KeyPair KEY_PAIR =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));

  private static final BytesValue PAYLOAD =
      BytesValue.fromHexString(
          "0x608060405234801561001057600080fd5b5060008054600160a060020a03191633179055610199806100326000396000f3fe6080604052600436106100565763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f245811461005b5780636057361d1461008257806367e404ce146100ae575b600080fd5b34801561006757600080fd5b506100706100ec565b60408051918252519081900360200190f35b34801561008e57600080fd5b506100ac600480360360208110156100a557600080fd5b50356100f2565b005b3480156100ba57600080fd5b506100c3610151565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200190f35b60025490565b604080513381526020810183905281517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5929181900390910190a16002556001805473ffffffffffffffffffffffffffffffffffffffff191633179055565b60015473ffffffffffffffffffffffffffffffffffffffff169056fea165627a7a72305820c7f729cb24e05c221f5aa913700793994656f233fe2ce3b9fd9a505ea17e8d8a0029");

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  public void reorg() throws IOException {
    final OrionTestHarness enclave =
        OrionTestHarnessFactory.create(
            folder.newFolder().toPath(),
            new OrionKeyConfiguration("enclavePublicKey", "enclavePrivateKey"));
    enclave.start();
    final Path dataDir = folder.newFolder().toPath();
    final Path dbDir = dataDir.resolve("database");
    final PrivacyParameters privacyParameters =
        new PrivacyParameters.Builder()
            .setEnabled(true)
            .setStorageProvider(createKeyValueStorageProvider(dataDir, dbDir))
            .setEnclaveUrl(enclave.clientUrl())
            .build();
    privacyParameters.setEnclavePublicKey("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

    final PrivateStateRootResolver psrr =
        new PrivateStateRootResolver(privacyParameters.getPrivateStateStorage());

    final BesuController besuController =
        new BesuController.Builder()
            .fromGenesisConfig(GenesisConfigFile.development())
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryStorageProvider())
            .networkId(BigInteger.ONE)
            .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
            .nodeKeys(SECP256K1.KeyPair.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .privacyParameters(privacyParameters)
            .transactionPoolConfiguration(TransactionPoolConfiguration.builder().build())
            .targetGasLimit(GasLimitCalculator.DEFAULT)
            .build();

    // Setup an initial blockchain
    final DefaultBlockchain blockchain =
        (DefaultBlockchain) besuController.getProtocolContext().getBlockchain();

    final List<Block> blocksToAdd =
        generateBlocks(
            new BlockDataGenerator.BlockOptions()
                .setBlockNumber(1)
                .setParentHash(
                    besuController.getProtocolContext().getBlockchain().getGenesisBlock().getHash())
                .addTransaction(
                    generateMarker(
                        BytesValues.fromBase64(
                            sendToEnclave(
                                generatePrivateTransaction(PAYLOAD, null), enclave.clientUrl())),
                        Address.DEFAULT_PRIVACY,
                        KEY_PAIR))
                .addOmmers()
                .setReceiptsRoot(
                    Hash.fromHexString(
                        "0xc8267b3f9ed36df3ff8adb51a6d030716f23eeb50270e7fce8d9822ffa7f0461"))
                .setGasUsed(23176)
                .setLogsBloom(LogsBloomFilter.empty())
                .setStateRoot(
                    Hash.fromHexString(
                        "0x08c4f995562906b70eeaa8f487747bed489229c70ff37d56d97cf17901f8b16e")));
    for (int i = 0; i < blocksToAdd.size(); i++) {
      if (!besuController
          .getProtocolSchedule()
          .getByBlockNumber(blockchain.getChainHeadBlockNumber())
          .getBlockImporter()
          .importBlock(
              besuController.getProtocolContext(), blocksToAdd.get(i), HeaderValidationMode.NONE)) {
        break;
      }
    }
    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(1);
    assertThat(
            psrr.resolveLastStateRoot(
                blockchain,
                blockchain.getChainHeadHeader(),
                BytesValues.fromBase64("8lDVI66RZHIrBsolz6Kn88Rd+WsJ4hUjb4hsh29xW/o=")))
        .isEqualTo(
            Hash.fromHexString(
                "0x2121b68f1333e93bae8cd717a3ca68c9d7e7003f6b288c36dfc59b0f87be9590"));
    // Create parallel fork of length 1
    final int forkBlock = 1;
    final BlockDataGenerator.BlockOptions options =
        new BlockDataGenerator.BlockOptions()
            .setBlockNumber(forkBlock)
            .setParentHash(
                besuController.getProtocolContext().getBlockchain().getGenesisBlock().getHash())
            .addTransaction()
            .addOmmers()
            .setDifficulty(
                besuController
                    .getProtocolContext()
                    .getBlockchain()
                    .getChainHeadHeader()
                    .getDifficulty()
                    .plus(10L))
            .setReceiptsRoot(
                Hash.fromHexString(
                    "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"))
            .setGasUsed(0)
            .setLogsBloom(LogsBloomFilter.empty())
            .setStateRoot(
                Hash.fromHexString(
                    "0x6553b1838b937f8f883600763505785cc227e9d99fa948f98566f0467f10e1af"));
    final Block fork = generateBlocks(options).get(0);

    besuController
        .getProtocolSchedule()
        .getByBlockNumber(blockchain.getChainHeadBlockNumber())
        .getBlockImporter()
        .importBlock(besuController.getProtocolContext(), fork, HeaderValidationMode.NONE);

    assertThat(
            psrr.resolveLastStateRoot(
                blockchain,
                blockchain.getChainHeadHeader(),
                BytesValues.fromBase64("8lDVI66RZHIrBsolz6Kn88Rd+WsJ4hUjb4hsh29xW/o=")))
        .isEqualTo(
            Hash.fromHexString(
                "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"));
  }

  private PrivateTransaction generatePrivateTransaction(
      final BytesValue payload, final Address to) {
    return PrivateTransaction.builder()
        .chainId(BigInteger.valueOf(2018))
        .gasLimit(1000)
        .gasPrice(Wei.ZERO)
        .nonce(0)
        .payload(payload)
        .to(to)
        .privateFrom(BytesValues.fromBase64("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo="))
        .privateFor(
            Collections.singletonList(
                BytesValues.fromBase64("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=")))
        .restriction(Restriction.RESTRICTED)
        .value(Wei.ZERO)
        .signAndBuild(KEY_PAIR);
  }

  private String sendToEnclave(final PrivateTransaction privateTransaction, final URI enclaveURI) {
    final Enclave enclave = new Enclave(enclaveURI);
    final SendRequest sendRequest = createSendRequest(privateTransaction);
    final SendResponse sendResponse;

    try {
      sendResponse = enclave.send(sendRequest);
      return sendResponse.getKey();
    } catch (Exception e) {
      throw e;
    }
  }

  private SendRequest createSendRequest(final PrivateTransaction privateTransaction) {
    final BytesValueRLPOutput bvrlp = new BytesValueRLPOutput();
    privateTransaction.writeTo(bvrlp);
    final String payload = BytesValues.asBase64String(bvrlp.encoded());

    if (privateTransaction.getPrivacyGroupId().isPresent()) {
      return new SendRequestBesu(
          payload,
          "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=",
          BytesValues.asBase64String(privateTransaction.getPrivacyGroupId().get()));
    } else {
      final List<String> privateFor =
          privateTransaction.getPrivateFor().get().stream()
              .map(BytesValues::asBase64String)
              .collect(Collectors.toList());

      // FIXME: orion should accept empty privateFor
      if (privateFor.isEmpty()) {
        privateFor.add(BytesValues.asBase64String(privateTransaction.getPrivateFrom()));
      }

      return new SendRequestLegacy(
          payload, BytesValues.asBase64String(privateTransaction.getPrivateFrom()), privateFor);
    }
  }

  private Transaction generateMarker(
      final BytesValue payload, final Address to, final SECP256K1.KeyPair keyPair) {
    return Transaction.builder()
        .chainId(BigInteger.valueOf(2018))
        .gasLimit(60000)
        .gasPrice(Wei.of(1000))
        .nonce(0)
        .payload(payload)
        .to(to)
        .value(Wei.ZERO)
        .signAndBuild(keyPair);
  }

  private List<Block> generateBlocks(final BlockDataGenerator.BlockOptions... blockOptions) {
    final List<Block> seq = new ArrayList<>(blockOptions.length);
    final BlockDataGenerator gen = new BlockDataGenerator();

    for (final BlockDataGenerator.BlockOptions blockOption : blockOptions) {
      final Block next = gen.block(blockOption);
      seq.add(next);
    }

    return seq;
  }

  //  private void assertBlockDataIsStored(
  //          final Blockchain blockchain, final Block block) {
  //    final Hash hash = block.getHash();
  //    assertThat(blockchain.getBlockHashByNumber(block.getHeader().getNumber()).get())
  //            .isEqualTo(hash);
  //    assertThat(blockchain.getBlockHeader(block.getHeader().getNumber()).get())
  //            .isEqualTo(block.getHeader());
  //    assertThat(blockchain.getBlockHeader(hash).get()).isEqualTo(block.getHeader());
  //    assertThat(blockchain.getBlockBody(hash).get()).isEqualTo(block.getBody());
  //    assertThat(blockchain.blockIsOnCanonicalChain(block.getHash())).isTrue();
  //
  //    final List<Transaction> txs = block.getBody().getTransactions();
  //    for (int i = 0; i < txs.size(); i++) {
  //      final Transaction expected = txs.get(i);
  //      final Transaction actual = blockchain.getTransactionByHash(expected.getHash()).get();
  //      assertThat(actual).isEqualTo(expected);
  //    }
  //  }
  //
  //  private void assertBlockIsHead(final Blockchain blockchain, final Block head) {
  //    assertThat(blockchain.getChainHeadHash()).isEqualTo(head.getHash());
  //    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(head.getHeader().getNumber());
  //    assertThat(blockchain.getChainHead().getHash()).isEqualTo(head.getHash());
  //  }
  //
  //  private void assertTotalDifficultiesAreConsistent(final Blockchain blockchain, final Block
  // head) {
  //    // Check that total difficulties are summed correctly
  //    long num = BlockHeader.GENESIS_BLOCK_NUMBER;
  //    UInt256 td = UInt256.of(0);
  //    while (num <= head.getHeader().getNumber()) {
  //      final Hash curHash = blockchain.getBlockHashByNumber(num).get();
  //      final BlockHeader curHead = blockchain.getBlockHeader(curHash).get();
  //      td = td.plus(curHead.getDifficulty());
  //      assertThat(blockchain.getTotalDifficultyByHash(curHash).get()).isEqualTo(td);
  //
  //      num += 1;
  //    }
  //
  //    // Check reported chainhead td
  //    assertThat(blockchain.getChainHead().getTotalDifficulty()).isEqualTo(td);
  //  }

  private StorageProvider createKeyValueStorageProvider(final Path dataDir, final Path dbDir) {
    return new KeyValueStorageProviderBuilder()
        .withStorageFactory(
            new RocksDBKeyValuePrivacyStorageFactory(
                () ->
                    new RocksDBFactoryConfiguration(
                        MAX_OPEN_FILES,
                        MAX_BACKGROUND_COMPACTIONS,
                        BACKGROUND_THREAD_COUNT,
                        CACHE_CAPACITY),
                Arrays.asList(KeyValueSegmentIdentifier.values())))
        .withCommonConfiguration(new BesuConfigurationImpl(dataDir, dbDir))
        .withMetricsSystem(new NoOpMetricsSystem())
        .build();
  }
}
