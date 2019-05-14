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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import tech.pegasys.pantheon.crypto.SecureRandomProvider;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderBuilder;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.LogsBloomFilter;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;
import tech.pegasys.pantheon.util.InvalidConfigurationException;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.security.SecureRandom;
import java.util.Collections;

import org.junit.Test;

public class GenesisBlockMismatchTest {
  private static final SecureRandom srand = SecureRandomProvider.publicSecureRandom();

  private static byte[] bytes(final int len) {
    final byte[] bytes = new byte[len];
    srand.nextBytes(bytes);
    return bytes;
  }

  @Test
  public void suppliedGenesisBlockMismatchStoredChainDataException() {
    final KeyValueStorage kvStore = new InMemoryKeyValueStorage();
    final BlockHeader genesisHeader00 =
        BlockHeaderBuilder.create()
            .parentHash(Hash.ZERO)
            .ommersHash(Hash.ZERO)
            .coinbase(Address.fromHexString("0x0000000000000000000000000000000000000000"))
            .stateRoot(Hash.ZERO)
            .transactionsRoot(Hash.ZERO)
            .receiptsRoot(Hash.ZERO)
            .logsBloom(new LogsBloomFilter(BytesValue.of(bytes(LogsBloomFilter.BYTE_SIZE))))
            .difficulty(UInt256.ZERO)
            .number(0L)
            .gasLimit(1L)
            .gasUsed(1L)
            .timestamp(0L)
            .extraData(Bytes32.wrap(bytes(Bytes32.SIZE)))
            .mixHash(Hash.ZERO)
            .nonce(0L)
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .buildBlockHeader();
    final BlockBody genesisBody00 = new BlockBody(Collections.emptyList(), Collections.emptyList());
    final Block genesisBlock00 = new Block(genesisHeader00, genesisBody00);
    final DefaultMutableBlockchain blockchain00 =
        new DefaultMutableBlockchain(
            genesisBlock00,
            new KeyValueStoragePrefixedKeyBlockchainStorage(
                kvStore, new MainnetBlockHeaderFunctions()),
            new NoOpMetricsSystem());

    final BlockHeader genesisHeader01 =
        BlockHeaderBuilder.create()
            .parentHash(Hash.ZERO)
            .ommersHash(Hash.ZERO)
            .coinbase(Address.fromHexString("0x0000000000000000000000000000000000000000"))
            .stateRoot(Hash.ZERO)
            .transactionsRoot(Hash.ZERO)
            .receiptsRoot(Hash.ZERO)
            .logsBloom(new LogsBloomFilter(BytesValue.of(bytes(LogsBloomFilter.BYTE_SIZE))))
            .difficulty(UInt256.ZERO)
            .number(0L)
            .gasLimit(1L)
            .gasUsed(1L)
            .timestamp(0L)
            .extraData(Bytes32.wrap(bytes(Bytes32.SIZE)))
            .mixHash(Hash.ZERO)
            .nonce(0L)
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .buildBlockHeader();
    final BlockBody genesisBody01 = new BlockBody(Collections.emptyList(), Collections.emptyList());
    final Block genesisBlock01 = new Block(genesisHeader01, genesisBody01);

    assertThatExceptionOfType(InvalidConfigurationException.class)
        .isThrownBy(() -> blockchain00.setGenesis(genesisBlock01))
        .withMessageContaining(
            "Supplied genesis block does not match stored chain data.\n"
                + "Please specify a different data directory with --data-path or specify the original genesis file with --genesis-file.");
  }
}
