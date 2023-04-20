/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
package org.hyperledger.besu.ethereum.bonsai.trielog;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.bonsai.worldview.StorageSlotKey;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockchainSetupUtil;
import org.hyperledger.besu.ethereum.worldstate.DataStorageFormat;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TrieLogFactoryTests {

  BlockchainSetupUtil setup = BlockchainSetupUtil.forTesting(DataStorageFormat.BONSAI);

  @Test
  public void testSerializeDeserializeAreEqual() {
    BlockHeader header =
        new BlockHeaderTestFixture()
            .parentHash(setup.getGenesisState().getBlock().getHash())
            .coinbase(Address.ZERO)
            .buildHeader();

    TrieLogLayer fixture =
        new TrieLogLayer()
            .setBlockHash(header.getBlockHash())
            .addAccountChange(
                Address.fromHexString("0xdeadbeef"),
                null,
                new StateTrieAccountValue(0, Wei.fromEth(1), Hash.EMPTY, Hash.EMPTY))
            .addCodeChange(
                Address.ZERO, null, Bytes.fromHexString("0xdeadbeef"), header.getBlockHash())
            .addStorageChange(Address.ZERO, new StorageSlotKey(UInt256.ZERO), null, UInt256.ONE);

    TrieLogFactory<TrieLogLayer> factory = new TrieLogFactoryImpl();
    byte[] rlp = factory.serialize(fixture);

    TrieLogLayer layer = factory.deserialize(rlp);
    assertThat(layer).isEqualTo(fixture);
  }
}
