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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toSet;

import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SecureRandomProvider;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class BlockDataGenerator {

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  private final Random random;
  private final KeyPairGenerator keyPairGenerator;

  public BlockDataGenerator(final int seed) {
    this.random = new Random(seed);
    keyPairGenerator = createKeyPairGenerator(seed);
  }

  public BlockDataGenerator() {
    this(1);
  }

  private KeyPairGenerator createKeyPairGenerator(final long seed) {
    final KeyPairGenerator keyPairGenerator;
    try {
      keyPairGenerator = KeyPairGenerator.getInstance(SECP256K1.ALGORITHM, SECP256K1.PROVIDER);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    final ECGenParameterSpec ecGenParameterSpec = new ECGenParameterSpec(SECP256K1.CURVE_NAME);
    try {
      final SecureRandom secureRandom = SecureRandomProvider.createSecureRandom();
      secureRandom.setSeed(seed);
      keyPairGenerator.initialize(ecGenParameterSpec, secureRandom);
    } catch (final InvalidAlgorithmParameterException e) {
      throw new RuntimeException(e);
    }
    return keyPairGenerator;
  }

  /**
   * Generates a sequence of blocks with some accounts and account storage pre-populated with random
   * data.
   */
  private List<Block> blockSequence(
      final int count,
      final long nextBlock,
      final Hash parent,
      final WorldStateArchive worldStateArchive,
      final List<Address> accountsToSetup,
      final List<UInt256> storageKeys) {
    final List<Block> seq = new ArrayList<>(count);

    final MutableWorldState worldState = worldStateArchive.getMutable();

    long nextBlockNumber = nextBlock;
    Hash parentHash = parent;

    for (int i = 0; i < count; i++) {
      final WorldUpdater stateUpdater = worldState.updater();
      if (i == 0) {
        // Set up some accounts
        accountsToSetup.forEach(stateUpdater::createAccount);
        stateUpdater.commit();
      } else {
        // Mutate accounts
        accountsToSetup.forEach(
            hash -> {
              final MutableAccount a = stateUpdater.getMutable(hash);
              a.incrementNonce();
              a.setBalance(Wei.of(positiveLong()));
              storageKeys.forEach(key -> a.setStorageValue(key, UInt256.ONE));
            });
        stateUpdater.commit();
      }
      final BlockOptions options =
          new BlockOptions()
              .setBlockNumber(nextBlockNumber)
              .setParentHash(parentHash)
              .setStateRoot(worldState.rootHash());
      final Block next = block(options);
      seq.add(next);
      parentHash = next.getHash();
      nextBlockNumber = nextBlockNumber + 1L;
      worldState.persist();
    }

    return seq;
  }

  public List<Account> createRandomAccounts(final MutableWorldState worldState, final int count) {
    return createRandomAccounts(worldState, count, .5f, .75f);
  }

  public List<Account> createRandomContractAccountsWithNonEmptyStorage(
      final MutableWorldState worldState, final int count) {
    return createRandomAccounts(worldState, count, 1f, 1f);
  }

  private List<Account> createRandomAccounts(
      final MutableWorldState worldState,
      final int count,
      final float percentContractAccounts,
      final float percentContractAccountsWithNonEmptyStorage) {
    WorldUpdater updater = worldState.updater();
    List<Account> accounts = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      MutableAccount account = updater.getOrCreate(address());
      if (random.nextFloat() < percentContractAccounts) {
        // Some percentage of accounts are contract accounts
        account.setCode(bytesValue(5, 50));
        account.setVersion(Account.DEFAULT_VERSION);
        if (random.nextFloat() < percentContractAccountsWithNonEmptyStorage) {
          // Add some storage for contract accounts
          int storageValues = random.nextInt(20) + 10;
          for (int j = 0; j < storageValues; j++) {
            account.setStorageValue(uint256(), uint256());
          }
        }
      }
      account.setNonce(random.nextInt(10));
      account.setBalance(Wei.of(positiveLong()));

      accounts.add(account);
    }
    updater.commit();
    worldState.persist();
    return accounts;
  }

  public List<Block> blockSequence(final int count) {
    final WorldStateArchive worldState = InMemoryStorageProvider.createInMemoryWorldStateArchive();
    return blockSequence(count, worldState, Collections.emptyList(), Collections.emptyList());
  }

  public List<Block> blockSequence(final Block previousBlock, final int count) {
    final WorldStateArchive worldState = InMemoryStorageProvider.createInMemoryWorldStateArchive();
    Hash parentHash = previousBlock.getHeader().getHash();
    long blockNumber = previousBlock.getHeader().getNumber() + 1;
    return blockSequence(
        count,
        blockNumber,
        parentHash,
        worldState,
        Collections.emptyList(),
        Collections.emptyList());
  }

  public List<Block> blockSequence(
      final int count,
      final WorldStateArchive worldStateArchive,
      final List<Address> accountsToSetup,
      final List<UInt256> storageKeys) {
    final long blockNumber = BlockHeader.GENESIS_BLOCK_NUMBER;
    final Hash parentHash = Hash.ZERO;
    return blockSequence(
        count, blockNumber, parentHash, worldStateArchive, accountsToSetup, storageKeys);
  }

  public Block genesisBlock() {
    final BlockOptions options =
        new BlockOptions()
            .setBlockNumber(BlockHeader.GENESIS_BLOCK_NUMBER)
            .setStateRoot(Hash.EMPTY_TRIE_HASH)
            .setParentHash(Hash.ZERO);
    return block(options);
  }

  public Block block(final BlockOptions options) {
    final long blockNumber = options.getBlockNumber(positiveLong());
    final BlockHeader header = header(blockNumber, options);
    final BlockBody body =
        blockNumber == BlockHeader.GENESIS_BLOCK_NUMBER ? BlockBody.empty() : body(options);
    return new Block(header, body);
  }

  public Block block() {
    return block(new BlockOptions());
  }

  public BlockOptions nextBlockOptions(final Block afterBlock) {
    return new BlockOptions()
        .setBlockNumber(afterBlock.getHeader().getNumber() + 1)
        .setParentHash(afterBlock.getHash());
  }

  public Block nextBlock(final Block afterBlock) {
    final BlockOptions options = nextBlockOptions(afterBlock);
    return block(options);
  }

  public BlockHeader header(final long blockNumber) {
    return header(blockNumber, new BlockOptions());
  }

  public BlockHeader header() {
    return header(positiveLong(), new BlockOptions());
  }

  public BlockHeader header(final long number, final BlockOptions options) {
    final int gasLimit = random.nextInt() & Integer.MAX_VALUE;
    final int gasUsed = Math.max(0, gasLimit - 1);
    final long blockNonce = random.nextLong();
    return BlockHeaderBuilder.create()
        .parentHash(options.getParentHash(hash()))
        .ommersHash(hash())
        .coinbase(address())
        .stateRoot(options.getStateRoot(hash()))
        .transactionsRoot(hash())
        .receiptsRoot(hash())
        .logsBloom(logsBloom())
        .difficulty(options.getDifficulty(uint256(4)))
        .number(number)
        .gasLimit(gasLimit)
        .gasUsed(gasUsed)
        .timestamp(Instant.now().truncatedTo(ChronoUnit.SECONDS).getEpochSecond())
        .extraData(options.getExtraData(bytes32()))
        .mixHash(hash())
        .nonce(blockNonce)
        .blockHeaderFunctions(options.getBlockHeaderFunctions(new MainnetBlockHeaderFunctions()))
        .buildBlockHeader();
  }

  public BlockBody body() {
    return body(new BlockOptions());
  }

  public BlockBody body(final BlockOptions options) {
    final List<BlockHeader> ommers = new ArrayList<>();
    final int ommerCount = random.nextInt(3);
    for (int i = 0; i < ommerCount; i++) {
      ommers.add(header());
    }
    final List<Transaction> defaultTxs = new ArrayList<>();
    defaultTxs.add(transaction());
    defaultTxs.add(transaction());

    return new BlockBody(options.getTransactions(defaultTxs), ommers);
  }

  public Transaction transaction(final BytesValue payload) {
    return Transaction.builder()
        .nonce(positiveLong())
        .gasPrice(Wei.wrap(bytes32()))
        .gasLimit(positiveLong())
        .to(address())
        .value(Wei.wrap(bytes32()))
        .payload(payload)
        .chainId(BigInteger.ONE)
        .signAndBuild(generateKeyPair());
  }

  public Transaction transaction() {
    return Transaction.builder()
        .nonce(positiveLong())
        .gasPrice(Wei.wrap(bytes32()))
        .gasLimit(positiveLong())
        .to(address())
        .value(Wei.wrap(bytes32()))
        .payload(bytes32())
        .chainId(BigInteger.ONE)
        .signAndBuild(generateKeyPair());
  }

  public Set<Transaction> transactions(final int n) {
    Wei gasPrice = Wei.wrap(bytes32());
    long gasLimit = positiveLong();
    Address to = address();
    Wei value = Wei.wrap(bytes32());
    int chainId = 1;
    Bytes32 payload = bytes32();
    final SECP256K1.Signature signature = SECP256K1.sign(payload, generateKeyPair());

    final Set<Transaction> txs =
        IntStream.range(0, n)
            .parallel()
            .mapToObj(
                v ->
                    new Transaction(
                        v,
                        gasPrice,
                        gasLimit,
                        Optional.of(to),
                        value,
                        signature,
                        payload,
                        to,
                        Optional.of(BigInteger.valueOf(chainId))))
            .collect(toSet());
    return txs;
  }

  public TransactionReceipt receipt(final long cumulativeGasUsed) {
    return new TransactionReceipt(
        hash(), cumulativeGasUsed, Arrays.asList(log(), log()), Optional.empty());
  }

  public TransactionReceipt receipt(final BytesValue revertReason) {
    return new TransactionReceipt(
        hash(), positiveLong(), Arrays.asList(log(), log()), Optional.of(revertReason));
  }

  public TransactionReceipt receipt() {
    return receipt(positiveLong());
  }

  public UInt256 storageKey() {
    return uint256();
  }

  public List<TransactionReceipt> receipts(final Block block) {
    final long totalGas = block.getHeader().getGasUsed();
    final int receiptCount = block.getBody().getTransactions().size();

    final List<TransactionReceipt> receipts = new ArrayList<>(receiptCount);
    for (int i = 0; i < receiptCount; i++) {
      receipts.add(receipt((totalGas * (i + 1)) / (receiptCount)));
    }

    return receipts;
  }

  public Log log() {
    return new Log(address(), bytesValue(5 + random.nextInt(10)), Collections.emptyList());
  }

  private Bytes32 bytes32() {
    return Bytes32.wrap(bytes(Bytes32.SIZE));
  }

  public BytesValue bytesValue(final int size) {
    return BytesValue.wrap(bytes(size));
  }

  /**
   * Creates a UInt256 with a value that fits within maxByteSize
   *
   * @param maxByteSize The byte size to cap this value to
   * @return
   */
  private UInt256 uint256(final int maxByteSize) {
    assert maxByteSize <= 32;
    return Bytes32.wrap(bytes(32, 32 - maxByteSize)).asUInt256();
  }

  private UInt256 uint256() {
    return bytes32().asUInt256();
  }

  private long positiveLong() {
    final long l = random.nextLong();
    return l < 0 ? Math.abs(l + 1) : l;
  }

  public Hash hash() {
    return Hash.wrap(bytes32());
  }

  public Address address() {
    return Address.wrap(bytesValue(Address.SIZE));
  }

  public LogsBloomFilter logsBloom() {
    return new LogsBloomFilter(BytesValue.of(bytes(LogsBloomFilter.BYTE_SIZE)));
  }

  public BytesValue bytesValue() {
    return bytesValue(1, 20);
  }

  public BytesValue bytesValue(final int minSize, final int maxSize) {
    checkArgument(minSize >= 0);
    checkArgument(maxSize >= 0);
    checkArgument(maxSize > minSize);
    final int size = random.nextInt(maxSize - minSize) + minSize;
    return BytesValue.wrap(bytes(size));
  }

  private byte[] bytes(final int size) {
    return bytes(size, 0);
  }

  /**
   * Creates a byte sequence with leading zeros.
   *
   * @param size The size of the byte array to return
   * @param zerofill The number of lower-order bytes to fill with zero (creating a smaller big
   *     endian integer value)
   * @return
   */
  private byte[] bytes(final int size, final int zerofill) {
    final byte[] bytes = new byte[size];
    random.nextBytes(bytes);
    Arrays.fill(bytes, 0, zerofill, (byte) 0x0);
    return bytes;
  }

  private SECP256K1.KeyPair generateKeyPair() {
    final java.security.KeyPair rawKeyPair = keyPairGenerator.generateKeyPair();
    final BCECPrivateKey privateKey = (BCECPrivateKey) rawKeyPair.getPrivate();
    final BCECPublicKey publicKey = (BCECPublicKey) rawKeyPair.getPublic();

    final BigInteger privateKeyValue = privateKey.getD();

    // Ethereum does not use encoded public keys like bitcoin - see
    // https://en.bitcoin.it/wiki/Elliptic_Curve_Digital_Signature_Algorithm for details
    // Additionally, as the first bit is a constant prefix (0x04) we ignore this value
    final byte[] publicKeyBytes = publicKey.getQ().getEncoded(false);
    final BigInteger publicKeyValue =
        new BigInteger(1, Arrays.copyOfRange(publicKeyBytes, 1, publicKeyBytes.length));

    return new SECP256K1.KeyPair(
        SECP256K1.PrivateKey.create(privateKeyValue), SECP256K1.PublicKey.create(publicKeyValue));
  }

  public static class BlockOptions {
    private OptionalLong blockNumber = OptionalLong.empty();
    private Optional<Hash> parentHash = Optional.empty();
    private Optional<Hash> stateRoot = Optional.empty();
    private Optional<UInt256> difficulty = Optional.empty();
    private Optional<List<Transaction>> transactions = Optional.empty();
    private Optional<BytesValue> extraData = Optional.empty();
    private Optional<BlockHeaderFunctions> blockHeaderFunctions = Optional.empty();

    public static BlockOptions create() {
      return new BlockOptions();
    }

    public List<Transaction> getTransactions(final List<Transaction> defaultValue) {
      return transactions.orElse(defaultValue);
    }

    public long getBlockNumber(final long defaultValue) {
      return blockNumber.orElse(defaultValue);
    }

    public Hash getParentHash(final Hash defaultValue) {
      return parentHash.orElse(defaultValue);
    }

    public Hash getStateRoot(final Hash defaultValue) {
      return stateRoot.orElse(defaultValue);
    }

    public UInt256 getDifficulty(final UInt256 defaultValue) {
      return difficulty.orElse(defaultValue);
    }

    public BytesValue getExtraData(final Bytes32 defaultValue) {
      return extraData.orElse(defaultValue);
    }

    public BlockHeaderFunctions getBlockHeaderFunctions(final BlockHeaderFunctions defaultValue) {
      return blockHeaderFunctions.orElse(defaultValue);
    }

    public BlockOptions addTransaction(final Transaction... tx) {
      if (!transactions.isPresent()) {
        transactions = Optional.of(new ArrayList<>());
      }
      transactions.get().addAll(Arrays.asList(tx));
      return this;
    }

    public BlockOptions setBlockNumber(final long blockNumber) {
      this.blockNumber = OptionalLong.of(blockNumber);
      return this;
    }

    public BlockOptions setParentHash(final Hash parentHash) {
      this.parentHash = Optional.of(parentHash);
      return this;
    }

    public BlockOptions setStateRoot(final Hash stateRoot) {
      this.stateRoot = Optional.of(stateRoot);
      return this;
    }

    public BlockOptions setDifficulty(final UInt256 difficulty) {
      this.difficulty = Optional.of(difficulty);
      return this;
    }

    public BlockOptions setExtraData(final BytesValue extraData) {
      this.extraData = Optional.of(extraData);
      return this;
    }

    public BlockOptions setBlockHeaderFunctions(final BlockHeaderFunctions blockHeaderFunctions) {
      this.blockHeaderFunctions = Optional.of(blockHeaderFunctions);
      return this;
    }
  }
}
