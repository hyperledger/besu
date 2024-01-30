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
package org.hyperledger.besu.ethereum.chain;

import static java.util.Collections.emptyList;
import static org.hyperledger.besu.ethereum.trie.common.GenesisWorldStateProvider.createGenesisWorldState;

import org.hyperledger.besu.config.GenesisAllocation;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public final class GenesisState {

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
   * @return A new {@link GenesisState}.
   */
  public static GenesisState fromJson(final String json, final ProtocolSchedule protocolSchedule) {
    return fromConfig(GenesisConfigFile.fromConfig(json), protocolSchedule);
  }

  /**
   * Construct a {@link GenesisState} from a JSON string.
   *
   * @param dataStorageConfiguration A {@link DataStorageConfiguration} describing the storage
   *     configuration
   * @param json A JSON string describing the genesis block
   * @param protocolSchedule A protocol Schedule associated with
   * @return A new {@link GenesisState}.
   */
  public static GenesisState fromJson(
      final DataStorageConfiguration dataStorageConfiguration,
      final String json,
      final ProtocolSchedule protocolSchedule) {
    return fromConfig(
        dataStorageConfiguration, GenesisConfigFile.fromConfig(json), protocolSchedule);
  }

  /**
   * Construct a {@link GenesisState} from a JSON object.
   *
   * @param config A {@link GenesisConfigFile} describing the genesis block.
   * @param protocolSchedule A protocol Schedule associated with
   * @return A new {@link GenesisState}.
   */
  public static GenesisState fromConfig(
      final GenesisConfigFile config, final ProtocolSchedule protocolSchedule) {
    return fromConfig(DataStorageConfiguration.DEFAULT_CONFIG, config, protocolSchedule);
  }

  /**
   * Construct a {@link GenesisState} from a JSON object.
   *
   * @param dataStorageConfiguration A {@link DataStorageConfiguration} describing the storage
   *     configuration
   * @param config A {@link GenesisConfigFile} describing the genesis block.
   * @param protocolSchedule A protocol Schedule associated with
   * @return A new {@link GenesisState}.
   */
  public static GenesisState fromConfig(
      final DataStorageConfiguration dataStorageConfiguration,
      final GenesisConfigFile config,
      final ProtocolSchedule protocolSchedule) {
    final List<GenesisAccount> genesisAccounts = parseAllocations(config).toList();
    final Block block =
        new Block(
            buildHeader(
                config,
                calculateGenesisStateHash(dataStorageConfiguration, genesisAccounts),
                protocolSchedule),
            buildBody(config));
    return new GenesisState(block, genesisAccounts);
  }

  private static BlockBody buildBody(final GenesisConfigFile config) {
    final Optional<List<Withdrawal>> withdrawals =
        isShanghaiAtGenesis(config) ? Optional.of(emptyList()) : Optional.empty();
    final Optional<List<Deposit>> deposits =
        isExperimentalEipsTimeAtGenesis(config) ? Optional.of(emptyList()) : Optional.empty();

    return new BlockBody(emptyList(), emptyList(), withdrawals, deposits);
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
    writeAccountsTo(target, genesisAccounts, block.getHeader());
  }

  private static void writeAccountsTo(
      final MutableWorldState target,
      final List<GenesisAccount> genesisAccounts,
      final BlockHeader rootHeader) {
    final WorldUpdater updater = target.updater();
    genesisAccounts.forEach(
        genesisAccount -> {
          final MutableAccount account = updater.getOrCreate(genesisAccount.address);
          account.setNonce(genesisAccount.nonce);
          account.setBalance(genesisAccount.balance);
          account.setCode(genesisAccount.code);
          genesisAccount.storage.forEach(account::setStorageValue);
        });
    updater.commit();
    target.persist(rootHeader);
  }

  private static Hash calculateGenesisStateHash(
      final DataStorageConfiguration dataStorageConfiguration,
      final List<GenesisAccount> genesisAccounts) {
    try (var worldState = createGenesisWorldState(dataStorageConfiguration)) {
      writeAccountsTo(worldState, genesisAccounts, null);
      return worldState.rootHash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static BlockHeader buildHeader(
      final GenesisConfigFile genesis,
      final Hash genesisRootHash,
      final ProtocolSchedule protocolSchedule) {

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
        .baseFee(genesis.getGenesisBaseFeePerGas().orElse(null))
        .withdrawalsRoot(isShanghaiAtGenesis(genesis) ? Hash.EMPTY_TRIE_HASH : null)
        .blobGasUsed(isCancunAtGenesis(genesis) ? parseBlobGasUsed(genesis) : null)
        .excessBlobGas(isCancunAtGenesis(genesis) ? parseExcessBlobGas(genesis) : null)
        .parentBeaconBlockRoot(
            (isCancunAtGenesis(genesis) ? parseParentBeaconBlockRoot(genesis) : null))
        .depositsRoot(isExperimentalEipsTimeAtGenesis(genesis) ? Hash.EMPTY_TRIE_HASH : null)
        .buildBlockHeader();
  }

  private static Address parseCoinbase(final GenesisConfigFile genesis) {
    return genesis
        .getCoinbase()
        .map(str -> withNiceErrorMessage("coinbase", str, Address::fromHexString))
        .orElseGet(() -> Address.wrap(Bytes.wrap(new byte[Address.SIZE])));
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

  private static Bytes parseExtraData(final GenesisConfigFile genesis) {
    return withNiceErrorMessage("extraData", genesis.getExtraData(), Bytes::fromHexString);
  }

  private static Difficulty parseDifficulty(final GenesisConfigFile genesis) {
    return withNiceErrorMessage("difficulty", genesis.getDifficulty(), Difficulty::fromHexString);
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

  private static long parseBlobGasUsed(final GenesisConfigFile genesis) {
    return withNiceErrorMessage(
        "blobGasUsed", genesis.getBlobGasUsed(), GenesisState::parseUnsignedLong);
  }

  private static BlobGas parseExcessBlobGas(final GenesisConfigFile genesis) {
    long excessBlobGas =
        withNiceErrorMessage(
            "excessBlobGas", genesis.getExcessBlobGas(), GenesisState::parseUnsignedLong);
    return BlobGas.of(excessBlobGas);
  }

  private static Bytes32 parseParentBeaconBlockRoot(final GenesisConfigFile genesis) {
    return withNiceErrorMessage(
        "parentBeaconBlockRoot", genesis.getParentBeaconBlockRoot(), Bytes32::fromHexString);
  }

  private static long parseUnsignedLong(final String value) {
    String v = value.toLowerCase(Locale.US);
    if (v.startsWith("0x")) {
      v = v.substring(2);
    }
    return Long.parseUnsignedLong(v, 16);
  }

  private static boolean isShanghaiAtGenesis(final GenesisConfigFile genesis) {
    final OptionalLong shanghaiTimestamp = genesis.getConfigOptions().getShanghaiTime();
    if (shanghaiTimestamp.isPresent()) {
      return genesis.getTimestamp() >= shanghaiTimestamp.getAsLong();
    }
    return false;
  }

  private static boolean isCancunAtGenesis(final GenesisConfigFile genesis) {
    final OptionalLong cancunTimestamp = genesis.getConfigOptions().getCancunTime();
    if (cancunTimestamp.isPresent()) {
      return genesis.getTimestamp() >= cancunTimestamp.getAsLong();
    }
    return false;
  }

  private static boolean isExperimentalEipsTimeAtGenesis(final GenesisConfigFile genesis) {
    final OptionalLong experimentalEipsTime = genesis.getConfigOptions().getExperimentalEipsTime();
    if (experimentalEipsTime.isPresent()) {
      return genesis.getTimestamp() >= experimentalEipsTime.getAsLong();
    }
    return false;
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
    final Bytes code;

    static GenesisAccount fromAllocation(final GenesisAllocation allocation) {
      return new GenesisAccount(
          allocation.getNonce(),
          allocation.getAddress(),
          allocation.getBalance(),
          allocation.getStorage(),
          allocation.getCode());
    }

    private GenesisAccount(
        final String hexNonce,
        final String hexAddress,
        final String balance,
        final Map<String, String> storage,
        final String hexCode) {
      this.nonce = withNiceErrorMessage("nonce", hexNonce, GenesisState::parseUnsignedLong);
      this.address = withNiceErrorMessage("address", hexAddress, Address::fromHexString);
      this.balance = withNiceErrorMessage("balance", balance, this::parseBalance);
      this.code = hexCode != null ? Bytes.fromHexString(hexCode) : null;
      this.storage = parseStorage(storage);
    }

    private Wei parseBalance(final String balance) {
      final BigInteger val;
      if (balance.startsWith("0x")) {
        val = new BigInteger(1, Bytes.fromHexStringLenient(balance).toArrayUnsafe());
      } else {
        val = new BigInteger(balance);
      }

      return Wei.of(val);
    }

    private Map<UInt256, UInt256> parseStorage(final Map<String, String> storage) {
      final Map<UInt256, UInt256> parsedStorage = new HashMap<>();
      storage.forEach(
          (key1, value1) -> {
            final UInt256 key = withNiceErrorMessage("storage key", key1, UInt256::fromHexString);
            final UInt256 value =
                withNiceErrorMessage("storage value", value1, UInt256::fromHexString);
            parsedStorage.put(key, value);
          });

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
          .toString();
    }
  }
}
