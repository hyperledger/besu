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

import org.hyperledger.besu.config.GenesisAccount;
import org.hyperledger.besu.config.GenesisFile;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public final class GenesisState {

  private final Block block;
  private final GenesisFile genesisFile;

  private GenesisState(final Block block, final GenesisFile genesisFile) {
    this.block = block;
    this.genesisFile = genesisFile;
  }

  /**
   * Construct a {@link GenesisState} from a JSON string.
   *
   * @param json A JSON string describing the genesis block
   * @param protocolSchedule A protocol Schedule associated with
   * @return A new {@link GenesisState}.
   */
  public static GenesisState fromJson(final String json, final ProtocolSchedule protocolSchedule) {
    return fromConfig(GenesisFile.fromConfig(json), protocolSchedule);
  }

  /**
   * Construct a {@link GenesisState} from a URL
   *
   * @param dataStorageConfiguration A {@link DataStorageConfiguration} describing the storage
   *     configuration
   * @param jsonSource A URL pointing to JSON genesis file
   * @param protocolSchedule A protocol Schedule associated with
   * @return A new {@link GenesisState}.
   */
  @VisibleForTesting
  static GenesisState fromJsonSource(
      final DataStorageConfiguration dataStorageConfiguration,
      final URL jsonSource,
      final ProtocolSchedule protocolSchedule) {
    return fromConfig(
        dataStorageConfiguration, GenesisFile.fromConfig(jsonSource), protocolSchedule);
  }

  /**
   * Construct a {@link GenesisState} from a genesis file object.
   *
   * @param config A {@link GenesisFile} describing the genesis block.
   * @param protocolSchedule A protocol Schedule associated with
   * @return A new {@link GenesisState}.
   */
  public static GenesisState fromConfig(
      final GenesisFile config, final ProtocolSchedule protocolSchedule) {
    return fromConfig(DataStorageConfiguration.DEFAULT_CONFIG, config, protocolSchedule);
  }

  /**
   * Construct a {@link GenesisState} from a JSON object.
   *
   * @param dataStorageConfiguration A {@link DataStorageConfiguration} describing the storage
   *     configuration
   * @param genesisFile A {@link GenesisFile} describing the genesis block.
   * @param protocolSchedule A protocol Schedule associated with
   * @return A new {@link GenesisState}.
   */
  public static GenesisState fromConfig(
      final DataStorageConfiguration dataStorageConfiguration,
      final GenesisFile genesisFile,
      final ProtocolSchedule protocolSchedule) {
    final var genesisStateRoot = calculateGenesisStateRoot(dataStorageConfiguration, genesisFile);
    final Block block =
        new Block(
            buildHeader(genesisFile, genesisStateRoot, protocolSchedule), buildBody(genesisFile));
    return new GenesisState(block, genesisFile);
  }

  /**
   * Construct a {@link GenesisState} from a JSON object.
   *
   * @param genesisStateRoot The root of the genesis state.
   * @param genesisFile A {@link GenesisFile} describing the genesis block.
   * @param protocolSchedule A protocol Schedule associated with
   * @return A new {@link GenesisState}.
   */
  public static GenesisState fromStorage(
      final Hash genesisStateRoot,
      final GenesisFile genesisFile,
      final ProtocolSchedule protocolSchedule) {
    final Block block =
        new Block(
            buildHeader(genesisFile, genesisStateRoot, protocolSchedule), buildBody(genesisFile));
    return new GenesisState(block, genesisFile);
  }

  private static BlockBody buildBody(final GenesisFile config) {
    final Optional<List<Withdrawal>> withdrawals =
        isShanghaiAtGenesis(config) ? Optional.of(emptyList()) : Optional.empty();

    return new BlockBody(emptyList(), emptyList(), withdrawals);
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
    writeAccountsTo(target, genesisFile.streamAllocations(), block.getHeader());
  }

  private static void writeAccountsTo(
      final MutableWorldState target,
      final Stream<GenesisAccount> genesisAccounts,
      final BlockHeader rootHeader) {
    final WorldUpdater updater = target.updater();
    genesisAccounts.forEach(
        genesisAccount -> {
          final MutableAccount account = updater.createAccount(genesisAccount.address());
          account.setNonce(genesisAccount.nonce());
          account.setBalance(genesisAccount.balance());
          account.setCode(genesisAccount.code());
          genesisAccount.storage().forEach(account::setStorageValue);
        });
    updater.commit();
    target.persist(rootHeader);
  }

  private static Hash calculateGenesisStateRoot(
      final DataStorageConfiguration dataStorageConfiguration, final GenesisFile genesisFile) {
    try (var worldState = createGenesisWorldState(dataStorageConfiguration)) {
      writeAccountsTo(worldState, genesisFile.streamAllocations(), null);
      return worldState.rootHash();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static BlockHeader buildHeader(
      final GenesisFile genesis,
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
        .requestsHash(isPragueAtGenesis(genesis) ? Hash.EMPTY_REQUESTS_HASH : null)
        .buildBlockHeader();
  }

  private static Address parseCoinbase(final GenesisFile genesis) {
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

  private static Hash parseParentHash(final GenesisFile genesis) {
    return withNiceErrorMessage("parentHash", genesis.getParentHash(), Hash::fromHexStringLenient);
  }

  private static Bytes parseExtraData(final GenesisFile genesis) {
    return withNiceErrorMessage("extraData", genesis.getExtraData(), Bytes::fromHexString);
  }

  private static Difficulty parseDifficulty(final GenesisFile genesis) {
    return withNiceErrorMessage("difficulty", genesis.getDifficulty(), Difficulty::fromHexString);
  }

  private static Hash parseMixHash(final GenesisFile genesis) {
    return withNiceErrorMessage("mixHash", genesis.getMixHash(), Hash::fromHexStringLenient);
  }

  private static long parseNonce(final GenesisFile genesis) {
    return withNiceErrorMessage("nonce", genesis.getNonce(), GenesisState::parseUnsignedLong);
  }

  private static long parseBlobGasUsed(final GenesisFile genesis) {
    return withNiceErrorMessage(
        "blobGasUsed", genesis.getBlobGasUsed(), GenesisState::parseUnsignedLong);
  }

  private static BlobGas parseExcessBlobGas(final GenesisFile genesis) {
    long excessBlobGas =
        withNiceErrorMessage(
            "excessBlobGas", genesis.getExcessBlobGas(), GenesisState::parseUnsignedLong);
    return BlobGas.of(excessBlobGas);
  }

  private static Bytes32 parseParentBeaconBlockRoot(final GenesisFile genesis) {
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

  private static boolean isShanghaiAtGenesis(final GenesisFile genesis) {
    final OptionalLong shanghaiTimestamp = genesis.getConfigOptions().getShanghaiTime();
    if (shanghaiTimestamp.isPresent()) {
      return genesis.getTimestamp() >= shanghaiTimestamp.getAsLong();
    }
    return isCancunAtGenesis(genesis);
  }

  private static boolean isCancunAtGenesis(final GenesisFile genesis) {
    final OptionalLong cancunTimestamp = genesis.getConfigOptions().getCancunTime();
    if (cancunTimestamp.isPresent()) {
      return genesis.getTimestamp() >= cancunTimestamp.getAsLong();
    }
    return isPragueAtGenesis(genesis) || isCancunEOFAtGenesis(genesis);
  }

  private static boolean isCancunEOFAtGenesis(final GenesisFile genesis) {
    final OptionalLong cancunEOFTimestamp = genesis.getConfigOptions().getCancunEOFTime();
    if (cancunEOFTimestamp.isPresent()) {
      return genesis.getTimestamp() >= cancunEOFTimestamp.getAsLong();
    }
    return false;
  }

  private static boolean isPragueAtGenesis(final GenesisFile genesis) {
    final OptionalLong pragueTimestamp = genesis.getConfigOptions().getPragueTime();
    if (pragueTimestamp.isPresent()) {
      return genesis.getTimestamp() >= pragueTimestamp.getAsLong();
    }
    return isOsakaAtGenesis(genesis);
  }

  private static boolean isOsakaAtGenesis(final GenesisFile genesis) {
    final OptionalLong osakaTimestamp = genesis.getConfigOptions().getOsakaTime();
    if (osakaTimestamp.isPresent()) {
      return genesis.getTimestamp() >= osakaTimestamp.getAsLong();
    }
    return isFutureEipsTimeAtGenesis(genesis);
  }

  private static boolean isFutureEipsTimeAtGenesis(final GenesisFile genesis) {
    final OptionalLong futureEipsTime = genesis.getConfigOptions().getFutureEipsTime();
    if (futureEipsTime.isPresent()) {
      return genesis.getTimestamp() >= futureEipsTime.getAsLong();
    }
    return isExperimentalEipsTimeAtGenesis(genesis);
  }

  private static boolean isExperimentalEipsTimeAtGenesis(final GenesisFile genesis) {
    final OptionalLong experimentalEipsTime = genesis.getConfigOptions().getExperimentalEipsTime();
    if (experimentalEipsTime.isPresent()) {
      return genesis.getTimestamp() >= experimentalEipsTime.getAsLong();
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("block", block).toString();
  }
}
