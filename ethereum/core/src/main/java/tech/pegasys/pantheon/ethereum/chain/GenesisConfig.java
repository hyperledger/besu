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

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderBuilder;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.LogsBloomFilter;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.development.DevelopmentProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import tech.pegasys.pantheon.ethereum.worldstate.DefaultMutableWorldState;
import tech.pegasys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.MoreObjects;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;

public final class GenesisConfig<C> {

  private static final BlockBody BODY =
      new BlockBody(Collections.emptyList(), Collections.emptyList());
  private static final String MAINNET_FILE = "mainnet.json";

  private final Block block;
  private final int chainId;
  private final ProtocolSchedule<C> protocolSchedule;
  private final List<GenesisAccount> genesisAccounts;

  private GenesisConfig(
      final Block block,
      final int chainId,
      final ProtocolSchedule<C> protocolSchedule,
      final List<GenesisAccount> genesisAccounts) {
    this.block = block;
    this.chainId = chainId;
    this.protocolSchedule = protocolSchedule;
    this.genesisAccounts = genesisAccounts;
  }

  public static GenesisConfig<Void> mainnet() {
    try {
      final JsonObject config =
          new JsonObject(
              Resources.toString(Resources.getResource(MAINNET_FILE), StandardCharsets.UTF_8));
      return GenesisConfig.fromConfig(
          config, MainnetProtocolSchedule.fromConfig(config.getJsonObject("config")));
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public static GenesisConfig<Void> development() {
    try {
      final JsonObject config =
          new JsonObject(
              Resources.toString(Resources.getResource("dev.json"), StandardCharsets.UTF_8));
      return GenesisConfig.fromConfig(
          config, DevelopmentProtocolSchedule.create(config.getJsonObject("config")));
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Construct a {@link GenesisConfig} from a JSON string.
   *
   * @param json A JSON string describing the genesis block
   * @param protocolSchedule A protocol Schedule associated with
   * @param <C> The consensus context type
   * @return A new genesis block.
   */
  public static <C> GenesisConfig<C> fromJson(
      final String json, final ProtocolSchedule<C> protocolSchedule) {
    return fromConfig(new JsonObject(json), protocolSchedule);
  }

  /**
   * Construct a {@link GenesisConfig} from a JSON object.
   *
   * @param jsonConfig A {@link JsonObject} describing the genesis block.
   * @param protocolSchedule A protocol Schedule associated with
   * @param <C> The consensus context type
   * @return A new genesis block.
   */
  @SuppressWarnings("unchecked")
  public static <C> GenesisConfig<C> fromConfig(
      final JsonObject jsonConfig, final ProtocolSchedule<C> protocolSchedule) {
    final Map<String, Object> definition = toNormalizedMap(jsonConfig);
    final List<GenesisAccount> genesisAccounts =
        parseAllocations(definition).collect(Collectors.toList());
    final Block block =
        new Block(
            buildHeader(definition, calculateGenesisStateHash(genesisAccounts), protocolSchedule),
            BODY);

    final Map<String, Object> config =
        (Map<String, Object>) definition.getOrDefault("config", Collections.emptyMap());
    final int chainId = (int) config.getOrDefault("chainId", 1);
    return new GenesisConfig<>(block, chainId, protocolSchedule, genesisAccounts);
  }

  public Block getBlock() {
    return block;
  }

  public int getChainId() {
    return chainId;
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
        account -> updater.getOrCreate(account.address).setBalance(account.balance));
    updater.commit();
    target.persist();
  }

  public ProtocolSchedule<C> getProtocolSchedule() {
    return protocolSchedule;
  }

  private static Hash calculateGenesisStateHash(final List<GenesisAccount> genesisAccounts) {
    final MutableWorldState worldState =
        new DefaultMutableWorldState(
            new KeyValueStorageWorldStateStorage(new InMemoryKeyValueStorage()));
    writeAccountsTo(worldState, genesisAccounts);
    return worldState.rootHash();
  }

  private static <C> BlockHeader buildHeader(
      final Map<String, Object> genesis,
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
        .gasLimit(parseGasLimit(genesis))
        .gasUsed(0L)
        .timestamp(parseTimestamp(genesis))
        .extraData(parseExtraData(genesis))
        .mixHash(parseMixHash(genesis))
        .nonce(parseNonce(genesis))
        .blockHashFunction(ScheduleBasedBlockHashFunction.create(protocolSchedule))
        .buildBlockHeader();
  }

  /* Converts the {@link JsonObject} describing the Genesis Block to a {@link Map}. This method
   * converts all nested {@link JsonObject} to {@link Map} as well. Also, note that all keys are
   * converted to lowercase for easier lookup since the keys in a 'genesis.json' file are assumed
   * case insensitive.
   */
  private static Map<String, Object> toNormalizedMap(final JsonObject genesis) {
    final Map<String, Object> normalized = new HashMap<>();
    genesis
        .getMap()
        .forEach(
            (key, value) -> {
              final String normalizedKey = key.toLowerCase(Locale.US);
              if (value instanceof JsonObject) {
                normalized.put(normalizedKey, toNormalizedMap((JsonObject) value));
              } else {
                normalized.put(normalizedKey, value);
              }
            });
    return normalized;
  }

  private static String getString(
      final Map<String, Object> map, final String key, final String def) {
    return entryAsString(map.getOrDefault(key.toLowerCase(Locale.US), def));
  }

  private static String getString(final Map<String, Object> map, final String key) {
    final String keyy = key.toLowerCase(Locale.US);
    if (!map.containsKey(keyy)) {
      throw new IllegalArgumentException(
          String.format("Invalid Genesis block configuration, missing value for '%s'", key));
    }
    return entryAsString(map.get(keyy));
  }

  private static String entryAsString(final Object value) {
    return ((CharSequence) value).toString();
  }

  private static long parseTimestamp(final Map<String, Object> genesis) {
    return Long.parseLong(getString(genesis, "timestamp", "0x0").substring(2), 16);
  }

  private static Address parseCoinbase(final Map<String, Object> genesis) {
    final Address coinbase;
    final String key = "coinbase";
    if (genesis.containsKey(key)) {
      coinbase = Address.fromHexString(getString(genesis, key));
    } else {
      coinbase = Address.wrap(BytesValue.wrap(new byte[Address.SIZE]));
    }
    return coinbase;
  }

  private static Hash parseParentHash(final Map<String, Object> genesis) {
    return Hash.wrap(Bytes32.fromHexString(getString(genesis, "parentHash", "")));
  }

  private static BytesValue parseExtraData(final Map<String, Object> genesis) {
    return BytesValue.fromHexString(getString(genesis, "extraData", ""));
  }

  private static UInt256 parseDifficulty(final Map<String, Object> genesis) {
    return UInt256.fromHexString(getString(genesis, "difficulty"));
  }

  private static long parseGasLimit(final Map<String, Object> genesis) {
    return Long.decode(getString(genesis, "gasLimit"));
  }

  private static Hash parseMixHash(final Map<String, Object> genesis) {
    return Hash.wrap(Bytes32.fromHexString(getString(genesis, "mixHash", "")));
  }

  private static long parseNonce(final Map<String, Object> genesis) {
    String nonce = getString(genesis, "nonce", "").toLowerCase();
    if (nonce.startsWith("0x")) {
      nonce = nonce.substring(2);
    }
    return Long.parseUnsignedLong(nonce, 16);
  }

  @SuppressWarnings("unchecked")
  private static Stream<GenesisAccount> parseAllocations(final Map<String, Object> genesis) {
    final Map<String, Object> alloc = (Map<String, Object>) genesis.get("alloc");
    return alloc
        .entrySet()
        .stream()
        .map(
            entry -> {
              final Address address = Address.fromHexString(entry.getKey());
              final String balance = getString((Map<String, Object>) entry.getValue(), "balance");
              return new GenesisAccount(address, balance);
            });
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("block", block)
        .add("chainId", chainId)
        .add("protocolSchedule", protocolSchedule)
        .add("genesisAccounts", genesisAccounts)
        .toString();
  }

  private static final class GenesisAccount {

    final Address address;
    final Wei balance;

    GenesisAccount(final Address address, final String balance) {
      this.address = address;
      this.balance = parseBalance(balance);
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

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("address", address)
          .add("balance", balance)
          .toString();
    }
  }
}
