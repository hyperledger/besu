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

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;
import tech.pegasys.pantheon.ethereum.worldstate.DefaultMutableWorldState;
import tech.pegasys.pantheon.ethereum.worldstate.KeyValueStorageWorldStateStorage;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public final class GenesisConfigTest {

  /** Known RLP encoded bytes of the Olympic Genesis Block. */
  private static final String OLYMPIC_RLP =
      "f901f8f901f3a00000000000000000000000000000000000000000000000000000000000000000a01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347940000000000000000000000000000000000000000a09178d0f23c965d81f0834a4c72c6253ce6830f4022b1359aaebfc1ecba442d4ea056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421b90100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000008302000080832fefd8808080a0000000000000000000000000000000000000000000000000000000000000000088000000000000002ac0c0";

  /** Known Hash of the Olympic Genesis Block. */
  private static final String OLYMPIC_HASH =
      "fd4af92a79c7fc2fd8bf0d342f2e832e1d4f485c85b9152d2039e03bc604fdca";

  @Test
  public void createFromJsonWithAllocs() throws Exception {
    final GenesisConfig<?> genesisConfig =
        GenesisConfig.fromJson(
            Resources.toString(
                GenesisConfigTest.class.getResource("genesis1.json"), Charsets.UTF_8),
            MainnetProtocolSchedule.create());
    final BlockHeader header = genesisConfig.getBlock().getHeader();
    assertThat(header.getStateRoot())
        .isEqualTo(
            Hash.fromHexString(
                "0x92683e6af0f8a932e5fe08c870f2ae9d287e39d4518ec544b0be451f1035fd39"));
    assertThat(header.getTransactionsRoot()).isEqualTo(Hash.EMPTY_TRIE_HASH);
    assertThat(header.getReceiptsRoot()).isEqualTo(Hash.EMPTY_TRIE_HASH);
    assertThat(header.getOmmersHash()).isEqualTo(Hash.EMPTY_LIST_HASH);
    assertThat(header.getExtraData()).isEqualTo(BytesValue.EMPTY);
    assertThat(header.getParentHash()).isEqualTo(Hash.ZERO);
    final DefaultMutableWorldState worldState =
        new DefaultMutableWorldState(
            new KeyValueStorageWorldStateStorage(new InMemoryKeyValueStorage()));
    genesisConfig.writeStateTo(worldState);
    final Account first =
        worldState.get(Address.fromHexString("0x0000000000000000000000000000000000000001"));
    final Account second =
        worldState.get(Address.fromHexString("0x0000000000000000000000000000000000000002"));
    assertThat(first).isNotNull();
    assertThat(first.getBalance().toLong()).isEqualTo(111111111L);
    assertThat(second).isNotNull();
    assertThat(second.getBalance().toLong()).isEqualTo(222222222L);
  }

  @Test
  public void createFromJsonNoAllocs() throws Exception {
    final GenesisConfig<?> genesisConfig =
        GenesisConfig.fromJson(
            Resources.toString(
                GenesisConfigTest.class.getResource("genesis2.json"), Charsets.UTF_8),
            MainnetProtocolSchedule.create());
    final BlockHeader header = genesisConfig.getBlock().getHeader();
    assertThat(header.getStateRoot()).isEqualTo(Hash.EMPTY_TRIE_HASH);
    assertThat(header.getTransactionsRoot()).isEqualTo(Hash.EMPTY_TRIE_HASH);
    assertThat(header.getReceiptsRoot()).isEqualTo(Hash.EMPTY_TRIE_HASH);
    assertThat(header.getOmmersHash()).isEqualTo(Hash.EMPTY_LIST_HASH);
    assertThat(header.getExtraData()).isEqualTo(BytesValue.EMPTY);
    assertThat(header.getParentHash()).isEqualTo(Hash.ZERO);
  }

  @Test
  public void encodeOlympicBlock() throws Exception {
    final GenesisConfig<?> genesisConfig =
        GenesisConfig.fromJson(
            Resources.toString(
                GenesisConfigTest.class.getResource("genesis-olympic.json"), Charsets.UTF_8),
            MainnetProtocolSchedule.create());
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    genesisConfig.getBlock().writeTo(tmp);
    assertThat(Hex.toHexString(genesisConfig.getBlock().getHeader().getHash().extractArray()))
        .isEqualTo(OLYMPIC_HASH);
    assertThat(Hex.toHexString(tmp.encoded().extractArray())).isEqualTo(OLYMPIC_RLP);
  }
}
