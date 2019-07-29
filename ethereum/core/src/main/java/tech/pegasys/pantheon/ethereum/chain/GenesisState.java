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
package tech.pegasys.pantheon.ethereum.chain;

import tech.pegasys.pantheon.config.GenesisAllocation;
import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderBuilder;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.LogsBloomFilter;
import tech.pegasys.pantheon.ethereum.core.MutableAccount;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import tech.pegasys.pantheon.ethereum.worldstate.DefaultMutableWorldState;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.MoreObjects;

public final class GenesisState {

  private static final BlockBody BODY =
      new BlockBody(Collections.emptyList(), Collections.emptyList());

  private final Block block;
  private final List<GenesisAccount> genesisAccounts;

  private GenesisState(final Block block, final List<GenesisAccount> genesisAccounts) {
    this.block = block;
    this.genesisAccounts = genesisAccounts;
  }

  /**
   * Construct a {@link GenesisState} from a JSON string.
   *
   * @param json A JSON string describing the genesis block
   * @param protocolSchedule A protocol Schedule associated with
   * @param <C> The consensus context type
   * @return A new {@link GenesisState}.
   */
  public static <C> GenesisState fromJson(
      final String json, final ProtocolSchedule<C> protocolSchedule) {
    return fromConfig(GenesisConfigFile.fromConfig(json), protocolSchedule);
  }

  /**
   * Construct a {@link GenesisState} from a JSON object.
   *
   * @param config A {@link GenesisConfigFile} describing the genesis block.
   * @param protocolSchedule A protocol Schedule associated with
   * @param <C> The consensus context type
   * @return A new {@link GenesisState}.
   */
  public static <C> GenesisState fromConfig(
      final GenesisConfigFile config, final ProtocolSchedule<C> protocolSchedule) {
    final List<GenesisAccount> genesisAccounts =
        parseAllocations(config).collect(Collectors.toList());
    final Block block =
        new Block(
            buildHeader(config, calculateGenesisStateHash(genesisAccounts), protocolSchedule),
            BODY);
    return new GenesisState(block, genesisAccounts);
  }

  public Block getBlock() {
    return block;
  }

  /**
   * Writes the genesis block's world state to the given {@link MutableWorldState}.
   *
   * @param target WorldView to write genesis state to
   */
  public void writeStateTo(final MutableWorldState target) {
    writeAccountsTo(target, genesisAccounts);
  }

  private static void writeAccountsTo(
      final MutableWorldState target, final List<GenesisAccount> genesisAccounts) {
    final WorldUpdater updater = target.updater();
    genesisAccounts.forEach(
        genesisAccount -> {
          final MutableAccount account = updater.getOrCreate(genesisAccount.address);
          account.setNonce(genesisAccount.nonce);
          account.setBalance(genesisAccount.balance);
          account.setCode(genesisAccount.code);
          account.setVersion(genesisAccount.version);
          genesisAccount.storage.forEach(account::setStorageValue);
        });
    updater.commit();
    target.persist();
  }

  private static Hash calculateGenesisStateHash(final List<GenesisAccount> genesisAccounts) {
    final WorldStateKeyValueStorage stateStorage =
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());
    final WorldStatePreimageKeyValueStorage preimageStorage =
        new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage());
    final MutableWorldState worldState =
        new DefaultMutableWorldState(stateStorage, preimageStorage);
    writeAccountsTo(worldState, genesisAccounts);
    return worldState.rootHash();
  }

  private static <C> BlockHeader buildHeader(
      final GenesisConfigFile genesis,
      final Hash genesisRootHash,
      final ProtocolSchedule<C> protocolSchedule) {

    return BlockHeaderBuilder.create()
        .parentHash(parseParentHash(genesis))
        .ommersHash(Hash.EMPTY_LIST_HASH)
        .coinbase(parseCoinbase(genesis))
        .stateRoot(genesisRootHash)
        .transactionsRoot(Hash.EMPTY_TRIE_HASH)
        .receiptsRoot(Hash.EMPTY_TRIE_HASH)
        .logsBloom(LogsBloomFilter.empty())
        .difficulty(parseDifficulty(genesis))
        .number(BlockHeader.GENESIS_BLOCK_NUMBER)
        .gasLimit(genesis.getGasLimit())
        .gasUsed(0L)
        .timestamp(genesis.getTimestamp())
        .extraData(parseExtraData(genesis))
        .mixHash(parseMixHash(genesis))
        .nonce(parseNonce(genesis))
        .blockHeaderFunctions(ScheduleBasedBlockHeaderFunctions.create(protocolSchedule))
        .buildBlockHeader();
  }

  private static Address parseCoinbase(final GenesisConfigFile genesis) {
    return genesis
        .getCoinbase()
        .map(str -> withNiceErrorMessage("coinbase", str, Address::fromHexString))
        .orElseGet(() -> Address.wrap(BytesValue.wrap(new byte[Address.SIZE])));
  }

  private static <T> T withNiceErrorMessage(
      final String name, final String value, final Function<String, T> parser) {
    try {
      return parser.apply(value);
    } catch (final IllegalArgumentException e) {
      throw createInvalidBlockConfigException(name, value, e);
    }
  }

  private static IllegalArgumentException createInvalidBlockConfigException(
      final String name, final String value, final IllegalArgumentException e) {
    return new IllegalArgumentException(
        "Invalid " + name + " in genesis block configuration: " + value, e);
  }

  private static Hash parseParentHash(final GenesisConfigFile genesis) {
    return withNiceErrorMessage("parentHash", genesis.getParentHash(), Hash::fromHexStringLenient);
  }

  private static BytesValue parseExtraData(final GenesisConfigFile genesis) {
    return withNiceErrorMessage("extraData", genesis.getExtraData(), BytesValue::fromHexString);
  }

  private static UInt256 parseDifficulty(final GenesisConfigFile genesis) {
    return withNiceErrorMessage("difficulty", genesis.getDifficulty(), UInt256::fromHexString);
  }

  private static Hash parseMixHash(final GenesisConfigFile genesis) {
    return withNiceErrorMessage("mixHash", genesis.getMixHash(), Hash::fromHexStringLenient);
  }

  private static Stream<GenesisAccount> parseAllocations(final GenesisConfigFile genesis) {
    return genesis.streamAllocations().map(GenesisAccount::fromAllocation);
  }

  private static long parseNonce(final GenesisConfigFile genesis) {
    return withNiceErrorMessage("nonce", genesis.getNonce(), GenesisState::parseUnsignedLong);
  }

  private static long parseUnsignedLong(final String value) {
    String nonce = value.toLowerCase(Locale.US);
    if (nonce.startsWith("0x")) {
      nonce = nonce.substring(2);
    }
    return Long.parseUnsignedLong(nonce, 16);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("block", block)
        .add("genesisAccounts", genesisAccounts)
        .toString();
  }

  private static final class GenesisAccount {

    final long nonce;
    final Address address;
    final Wei balance;
    final Map<UInt256, UInt256> storage;
    final BytesValue code;
    final int version;

    static GenesisAccount fromAllocation(final GenesisAllocation allocation) {
      return new GenesisAccount(
          allocation.getNonce(),
          allocation.getAddress(),
          allocation.getBalance(),
          allocation.getStorage(),
          allocation.getCode(),
          allocation.getVersion());
    }

    private GenesisAccount(
        final String hexNonce,
        final String hexAddress,
        final String balance,
        final Map<String, Object> storage,
        final String hexCode,
        final String version) {
      this.nonce = withNiceErrorMessage("nonce", hexNonce, GenesisState::parseUnsignedLong);
      this.address = withNiceErrorMessage("address", hexAddress, Address::fromHexString);
      this.balance = withNiceErrorMessage("balance", balance, this::parseBalance);
      this.code = hexCode != null ? BytesValue.fromHexString(hexCode) : null;
      this.version = version != null ? Integer.decode(version) : Account.DEFAULT_VERSION;
      this.storage = parseStorage(storage);
    }

    private Wei parseBalance(final String balance) {
      final BigInteger val;
      if (balance.startsWith("0x")) {
        val = new BigInteger(1, BytesValue.fromHexStringLenient(balance).extractArray());
      } else {
        val = new BigInteger(balance);
      }

      return Wei.of(val);
    }

    private Map<UInt256, UInt256> parseStorage(final Map<String, Object> storage) {
      final Map<UInt256, UInt256> parsedStorage = new HashMap<>();
      storage.forEach(
          (key, value) ->
              parsedStorage.put(
                  withNiceErrorMessage("storage key", key, UInt256::fromHexString),
                  withNiceErrorMessage(
                      "storage value", String.valueOf(value), UInt256::fromHexString)));
      return parsedStorage;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("address", address)
          .add("nonce", nonce)
          .add("balance", balance)
          .add("storage", storage)
          .add("code", code)
          .add("version", version)
          .toString();
    }
  }
}
