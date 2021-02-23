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
package org.hyperledger.besu.consensus.qbt.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import org.hyperledger.besu.consensus.common.bft.BftBlockHashing;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.qbt.support.blockheader.BftBlockHeaderHashTestCaseSpec;
import org.hyperledger.besu.consensus.qbt.support.provider.JsonProvider;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BftBlockHeaderHashTest {
  private static final String TEST_CONFIG_PATH = "BftBlockHeaderHashTests/";
  private final BftBlockHeaderHashTestCaseSpec spec;
  private static final JsonProvider JSON_PROVIDER = new JsonProvider();

  @Parameterized.Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(
            BftBlockHeaderHashTestCaseSpec.class, JSON_PROVIDER.getObjectMapper())
        .generate(TEST_CONFIG_PATH);
  }

  public BftBlockHeaderHashTest(
      final String name, final BftBlockHeaderHashTestCaseSpec spec, final boolean runTest) {
    this.spec = spec;
    assumeTrue("Test [" + name + "] was blacklisted", runTest);
  }

  @Test
  public void calculateQbftBlockHeaderHash() {
    final BlockHeader blockHeader = spec.getBlockHeader();
    final Hash onChainHash = BftBlockHashing.calculateHashOfBftBlockOnChain(blockHeader);
    final Hash committedSealHash = BftBlockHashing.calculateDataHashForCommittedSeal(blockHeader);
    final List<Address> committerAddresses =
        BftBlockHashing.recoverCommitterAddresses(blockHeader, BftExtraData.decode(blockHeader));

    assertThat(onChainHash).isEqualTo(spec.getOnChainHash());
    assertThat(committedSealHash).isEqualTo(spec.getCommittedSealHash());
    assertThat(committerAddresses).hasSameElementsAs(committersAddress());
  }

  private static List<Address> committersAddress() {
    return IntStream.rangeClosed(1, 4)
        .mapToObj(
            i ->
                Util.publicKeyToAddress(
                    NodeKeyUtils.createFrom(
                            (SECP256K1.KeyPair.create(
                                SECP256K1.PrivateKey.create(UInt256.valueOf(i).toBytes()))))
                        .getPublicKey()))
        .collect(Collectors.toList());
  }
}
