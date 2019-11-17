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
import org.hyperledger.besu.ethereum.ProtocolContext;
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
import org.hyperledger.besu.util.uint.UInt256;
import org.hyperledger.orion.testutil.OrionKeyConfiguration;
import org.hyperledger.orion.testutil.OrionTestHarness;
import org.hyperledger.orion.testutil.OrionTestHarnessFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

@SuppressWarnings("rawtypes")
public class PrivacyReorgTest {
  private static final int MAX_OPEN_FILES = 1024;
  private static final long CACHE_CAPACITY = 8388608;
  private static final int MAX_BACKGROUND_COMPACTIONS = 4;
  private static final int BACKGROUND_THREAD_COUNT = 4;

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private static final SECP256K1.KeyPair KEY_PAIR =
      SECP256K1.KeyPair.create(
          SECP256K1.PrivateKey.create(
              new BigInteger(
                  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63", 16)));
  private static final BytesValue ENCLAVE_PUBLIC_KEY =
      BytesValues.fromBase64("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

  // EventEmitter contract binary
  private static final BytesValue MOCK_PAYLOAD =
      BytesValue.fromHexString(
          "0x608060405234801561001057600080fd5b5060008054600160a060020a03191633179055610199806100326000396000f3fe6080604052600436106100565763ffffffff7c01000000000000000000000000000000000000000000000000000000006000350416633fa4f245811461005b5780636057361d1461008257806367e404ce146100ae575b600080fd5b34801561006757600080fd5b506100706100ec565b60408051918252519081900360200190f35b34801561008e57600080fd5b506100ac600480360360208110156100a557600080fd5b50356100f2565b005b3480156100ba57600080fd5b506100c3610151565b6040805173ffffffffffffffffffffffffffffffffffffffff9092168252519081900360200190f35b60025490565b604080513381526020810183905281517fc9db20adedc6cf2b5d25252b101ab03e124902a73fcb12b753f3d1aaa2d8f9f5929181900390910190a16002556001805473ffffffffffffffffffffffffffffffffffffffff191633179055565b60015473ffffffffffffffffffffffffffffffffffffffff169056fea165627a7a72305820c7f729cb24e05c221f5aa913700793994656f233fe2ce3b9fd9a505ea17e8d8a0029");
  private static final PrivateTransaction PRIVATE_TRANSACTION =
      PrivateTransaction.builder()
          .chainId(BigInteger.valueOf(2018))
          .gasLimit(1000)
          .gasPrice(Wei.ZERO)
          .nonce(0)
          .payload(MOCK_PAYLOAD)
          .to(null)
          .privateFrom(ENCLAVE_PUBLIC_KEY)
          .privateFor(Collections.singletonList(ENCLAVE_PUBLIC_KEY))
          .restriction(Restriction.RESTRICTED)
          .value(Wei.ZERO)
          .signAndBuild(KEY_PAIR);

  private BlockDataGenerator gen = new BlockDataGenerator();
  private BesuController besuController;
  private OrionTestHarness enclave;
  private PrivateStateRootResolver privateStateRootResolver;

  @Before
  public void setUp() throws IOException {

    // Start Enclave
    enclave =
        OrionTestHarnessFactory.create(
            folder.newFolder().toPath(),
            new OrionKeyConfiguration("enclavePublicKey", "enclavePrivateKey"));
    enclave.start();

    // Create Storage
    final Path dataDir = folder.newFolder().toPath();
    final Path dbDir = dataDir.resolve("database");

    // Configure Privacy
    final PrivacyParameters privacyParameters =
        new PrivacyParameters.Builder()
            .setEnabled(true)
            .setStorageProvider(createKeyValueStorageProvider(dataDir, dbDir))
            .setEnclaveUrl(enclave.clientUrl())
            .build();
    privacyParameters.setEnclavePublicKey("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");

    privateStateRootResolver =
        new PrivateStateRootResolver(privacyParameters.getPrivateStateStorage());

    besuController =
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
  }

  @Test
  public void reorgToChainAtEqualHeight() {
    // Setup an initial blockchain with one private transaction
    final ProtocolContext<?> protocolContext = besuController.getProtocolContext();
    final DefaultBlockchain blockchain = (DefaultBlockchain) protocolContext.getBlockchain();

    final Block firstBlock =
        gen.block(
            new BlockDataGenerator.BlockOptions()
                .setBlockNumber(1)
                .setParentHash(blockchain.getGenesisBlock().getHash())
                .addTransaction(
                    generateMarker(BytesValues.fromBase64(sendToEnclave(enclave.clientUrl()))))
                .addOmmers()
                .setReceiptsRoot(
                    Hash.fromHexString(
                        "0xc8267b3f9ed36df3ff8adb51a6d030716f23eeb50270e7fce8d9822ffa7f0461"))
                .setGasUsed(23176)
                .setLogsBloom(LogsBloomFilter.empty())
                .setStateRoot(
                    Hash.fromHexString(
                        "0x08c4f995562906b70eeaa8f487747bed489229c70ff37d56d97cf17901f8b16e")));

    appendBlock(besuController, blockchain, protocolContext, firstBlock);

    // Check that the private state root is not the empty state
    assertPrivateStateRoot(
        privateStateRootResolver,
        blockchain,
        "0x2121b68f1333e93bae8cd717a3ca68c9d7e7003f6b288c36dfc59b0f87be9590");

    // Create parallel fork of length 1 which removes privacy marker transaction
    final BlockDataGenerator.BlockOptions options =
        new BlockDataGenerator.BlockOptions()
            .setBlockNumber(firstBlock.getHeader().getNumber())
            .setParentHash(blockchain.getGenesisBlock().getHash())
            .addTransaction()
            .addOmmers()
            .setDifficulty(blockchain.getChainHeadHeader().getDifficulty().plus(10L))
            .setReceiptsRoot(
                Hash.fromHexString(
                    "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"))
            .setGasUsed(0)
            .setLogsBloom(LogsBloomFilter.empty())
            .setStateRoot(
                Hash.fromHexString(
                    "0x22498d301ccc0b130a57b83fa8d1b3e3cff44245de36bbec3643135009f2a106"));
    final Block forkBlock = gen.block(options);

    appendBlock(besuController, blockchain, protocolContext, forkBlock);

    // Check that the private state root is the empty state
    assertPrivateStateRoot(
        privateStateRootResolver,
        blockchain,
        "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
  }

  @Test
  public void reorgToShorterChain() {
    // Setup an initial blockchain with one private transaction
    final ProtocolContext<?> protocolContext = besuController.getProtocolContext();
    final DefaultBlockchain blockchain = (DefaultBlockchain) protocolContext.getBlockchain();

    final Block firstBlock =
        gen.block(
            new BlockDataGenerator.BlockOptions()
                .setBlockNumber(1)
                .setParentHash(blockchain.getGenesisBlock().getHash())
                .addTransaction()
                .addOmmers()
                .setReceiptsRoot(
                    Hash.fromHexString(
                        "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"))
                .setGasUsed(0)
                .setLogsBloom(LogsBloomFilter.empty())
                .setStateRoot(
                    Hash.fromHexString(
                        "0x6553b1838b937f8f883600763505785cc227e9d99fa948f98566f0467f10e1af")));

    final Block secondBlock =
        gen.block(
            new BlockDataGenerator.BlockOptions()
                .setBlockNumber(2)
                .setParentHash(firstBlock.getHeader().getHash())
                .addTransaction(
                    generateMarker(BytesValues.fromBase64(sendToEnclave(enclave.clientUrl()))))
                .addOmmers()
                .setReceiptsRoot(
                    Hash.fromHexString(
                        "0xc8267b3f9ed36df3ff8adb51a6d030716f23eeb50270e7fce8d9822ffa7f0461"))
                .setGasUsed(23176)
                .setLogsBloom(LogsBloomFilter.empty())
                .setStateRoot(
                    Hash.fromHexString(
                        "0xfd800d0839a542354a8244141a462f133a87c82cdf8320e25141c236805cb536")));

    appendBlock(besuController, blockchain, protocolContext, firstBlock);
    appendBlock(besuController, blockchain, protocolContext, secondBlock);

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(2);

    // Check that the private state root is not the empty state
    assertPrivateStateRoot(
        privateStateRootResolver,
        blockchain,
        "0x2121b68f1333e93bae8cd717a3ca68c9d7e7003f6b288c36dfc59b0f87be9590");

    // Create parallel fork of length 1 which removes privacy marker transaction
    final UInt256 remainingDifficultyToOutpace =
        blockchain
            .getBlockByNumber(1)
            .get()
            .getHeader()
            .getDifficulty()
            .plus(blockchain.getBlockByNumber(2).get().getHeader().getDifficulty());
    final BlockDataGenerator.BlockOptions options =
        new BlockDataGenerator.BlockOptions()
            .setBlockNumber(1)
            .setParentHash(blockchain.getGenesisBlock().getHash())
            .addTransaction()
            .addOmmers()
            .setDifficulty(remainingDifficultyToOutpace.plus(10L))
            .setReceiptsRoot(
                Hash.fromHexString(
                    "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"))
            .setGasUsed(0)
            .setLogsBloom(LogsBloomFilter.empty())
            .setStateRoot(
                Hash.fromHexString(
                    "0xbe746556c8799490a1a726503f403c3e9526c76676071ecc5972c5501a70421b"));
    final Block forkBlock = gen.block(options);

    appendBlock(besuController, blockchain, protocolContext, forkBlock);

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(1);

    // Check that the private state root is the empty state
    assertPrivateStateRoot(
        privateStateRootResolver,
        blockchain,
        "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
  }

  @Test
  public void reorgToLongerChain() {
    // Setup an initial blockchain with one private transaction
    final ProtocolContext<?> protocolContext = besuController.getProtocolContext();
    final DefaultBlockchain blockchain = (DefaultBlockchain) protocolContext.getBlockchain();

    final Block firstBlock =
        gen.block(
            new BlockDataGenerator.BlockOptions()
                .setBlockNumber(1)
                .setParentHash(blockchain.getGenesisBlock().getHeader().getHash())
                .addTransaction(
                    generateMarker(BytesValues.fromBase64(sendToEnclave(enclave.clientUrl()))))
                .addOmmers()
                .setReceiptsRoot(
                    Hash.fromHexString(
                        "0xc8267b3f9ed36df3ff8adb51a6d030716f23eeb50270e7fce8d9822ffa7f0461"))
                .setGasUsed(23176)
                .setLogsBloom(LogsBloomFilter.empty())
                .setStateRoot(
                    Hash.fromHexString(
                        "0x08c4f995562906b70eeaa8f487747bed489229c70ff37d56d97cf17901f8b16e")));

    appendBlock(besuController, blockchain, protocolContext, firstBlock);

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(1);

    // Check that the private state root is not the empty state
    assertPrivateStateRoot(
        privateStateRootResolver,
        blockchain,
        "0x2121b68f1333e93bae8cd717a3ca68c9d7e7003f6b288c36dfc59b0f87be9590");

    // Create parallel fork of length 1 which removes privacy marker transaction
    final Block forkBlock =
        gen.block(
            new BlockDataGenerator.BlockOptions()
                .setBlockNumber(1)
                .setParentHash(blockchain.getGenesisBlock().getHash())
                .addTransaction()
                .addOmmers()
                .setDifficulty(firstBlock.getHeader().getDifficulty().plus(10L))
                .setReceiptsRoot(
                    Hash.fromHexString(
                        "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"))
                .setGasUsed(0)
                .setLogsBloom(LogsBloomFilter.empty())
                .setStateRoot(
                    Hash.fromHexString(
                        "0x22498d301ccc0b130a57b83fa8d1b3e3cff44245de36bbec3643135009f2a106")));

    // Check that the private state root did not change
    assertPrivateStateRoot(
        privateStateRootResolver,
        blockchain,
        "0x2121b68f1333e93bae8cd717a3ca68c9d7e7003f6b288c36dfc59b0f87be9590");

    final Block secondForkBlock =
        gen.block(
            new BlockDataGenerator.BlockOptions()
                .setBlockNumber(2)
                .setParentHash(forkBlock.getHeader().getHash())
                .addTransaction()
                .addOmmers()
                .setDifficulty(firstBlock.getHeader().getDifficulty().plus(10L))
                .setReceiptsRoot(
                    Hash.fromHexString(
                        "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"))
                .setGasUsed(0)
                .setLogsBloom(LogsBloomFilter.empty())
                .setStateRoot(
                    Hash.fromHexString(
                        "0x1d73b20ca2d0f548aed15dd767c6583c361c4dcc5eb9f1e66021488f3b42bede")));

    appendBlock(besuController, blockchain, protocolContext, forkBlock);
    appendBlock(besuController, blockchain, protocolContext, secondForkBlock);

    assertThat(blockchain.getChainHeadBlockNumber()).isEqualTo(2);

    // Check that the private state root is the empty state
    assertPrivateStateRoot(
        privateStateRootResolver,
        blockchain,
        "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421");
  }

  @SuppressWarnings("unchecked")
  private void appendBlock(
      final BesuController besuController,
      final DefaultBlockchain blockchain,
      final ProtocolContext<?> protocolContext,
      final Block block) {
    besuController
        .getProtocolSchedule()
        .getByBlockNumber(blockchain.getChainHeadBlockNumber())
        .getBlockImporter()
        .importBlock(protocolContext, block, HeaderValidationMode.NONE);
  }

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

  private String sendToEnclave(final URI enclaveURI) {
    final Enclave enclave = new Enclave(enclaveURI);
    final SendRequest sendRequest = createSendRequest(PRIVATE_TRANSACTION);
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

  private Transaction generateMarker(final BytesValue payload) {
    return Transaction.builder()
        .chainId(BigInteger.valueOf(2018))
        .gasLimit(60000)
        .gasPrice(Wei.of(1000))
        .nonce(0)
        .payload(payload)
        .to(Address.DEFAULT_PRIVACY)
        .value(Wei.ZERO)
        .signAndBuild(KEY_PAIR);
  }

  private void assertPrivateStateRoot(
      final PrivateStateRootResolver psrr,
      final DefaultBlockchain blockchain,
      final String expected) {
    assertThat(
            psrr.resolveLastStateRoot(
                blockchain,
                blockchain.getChainHeadHeader(),
                BytesValues.fromBase64("8lDVI66RZHIrBsolz6Kn88Rd+WsJ4hUjb4hsh29xW/o=")))
        .isEqualTo(Hash.fromHexString(expected));
  }
}
