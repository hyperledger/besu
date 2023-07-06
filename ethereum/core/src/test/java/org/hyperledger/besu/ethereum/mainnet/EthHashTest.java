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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;

public final class EthHashTest {

  /**
   * Verifies hashing against block 300005 of the public Ethereum chain.
   *
   * @throws Exception On Failure
   */
  @Test
  public void hashimotoLight() throws Exception {
    final RLPInput input =
        new BytesValueRLPInput(
            Bytes.wrap(Resources.toByteArray(EthHashTest.class.getResource("block_300005.blocks"))),
            false);
    input.enterList();
    final BlockHeader header = BlockHeader.readFrom(input, new MainnetBlockHeaderFunctions());
    final long blockNumber = header.getNumber();
    final EpochCalculator epochCalculator = new EpochCalculator.DefaultEpochCalculator();
    final long epoch = epochCalculator.cacheEpoch(blockNumber);
    final long datasetSize = EthHash.datasetSize(epoch);
    final long cacheSize = EthHash.cacheSize(epoch);
    assertThat(datasetSize).isEqualTo(1157627776);
    assertThat(cacheSize).isEqualTo(18087488);
    final int[] cache = EthHash.mkCache((int) cacheSize, blockNumber, epochCalculator);
    PoWSolution solution =
        EthHash.hashimotoLight(datasetSize, cache, EthHash.hashHeader(header), header.getNonce());
    assertThat(solution.getMixHash()).isEqualTo(header.getMixHash());
  }

  @Test
  public void hashimotoLightExample() {
    final int[] cache = EthHash.mkCache(1024, 1L, new EpochCalculator.DefaultEpochCalculator());
    PoWSolution solution =
        EthHash.hashimotoLight(
            32 * 1024,
            cache,
            Bytes.fromHexString("c9149cc0386e689d789a1c2f3d5d169a61a6218ed30e74414dc736e442ef3d1f"),
            0L);

    assertThat(solution.getSolution().toHexString())
        .isEqualTo("0xd3539235ee2e6f8db665c0a72169f55b7f6c605712330b778ec3944f0eb5a557");
    assertThat(solution.getMixHash().toHexString())
        .isEqualTo("0xe4073cffaef931d37117cefd9afd27ea0f1cad6a981dd2605c4a1ac97c519800");
  }

  @Test
  public void prepareCache() {
    final int[] cache = EthHash.mkCache(1024, 1L, new EpochCalculator.DefaultEpochCalculator());
    final ByteBuffer buffer =
        ByteBuffer.allocate(cache.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    for (final int i : cache) {
      buffer.putInt(i);
    }
    assertThat(Hex.toHexString(buffer.array()))
        .isEqualTo(
            new StringBuilder()
                .append(
                    "7ce2991c951f7bf4c4c1bb119887ee07871eb5339d7b97b8588e85c742de90e5bafd5bbe6ce93a134fb6be9ad3e30db99d9528a2ea7846833f52e9ca119b6b54")
                .append(
                    "8979480c46e19972bd0738779c932c1b43e665a2fd3122fc3ddb2691f353ceb0ed3e38b8f51fd55b6940290743563c9f8fa8822e611924657501a12aafab8a8d")
                .append(
                    "88fb5fbae3a99d14792406672e783a06940a42799b1c38bc28715db6d37cb11f9f6b24e386dc52dd8c286bd8c36fa813dffe4448a9f56ebcbeea866b42f68d22")
                .append(
                    "6c32aae4d695a23cab28fd74af53b0c2efcc180ceaaccc0b2e280103d097a03c1d1b0f0f26ce5f32a90238f9bc49f645db001ef9cd3d13d44743f841fad11a37")
                .append(
                    "fa290c62c16042f703578921f30b9951465aae2af4a5dad43a7341d7b4a62750954965a47a1c3af638dc3495c4d62a9bab843168c9fc0114e79cffd1b2827b01")
                .append(
                    "75d30ba054658f214e946cf24c43b40d3383fbb0493408e5c5392434ca21bbcf43200dfb876c713d201813934fa485f48767c5915745cf0986b1dc0f33e57748")
                .append(
                    "bf483ee2aff4248dfe461ec0504a13628401020fc22638584a8f2f5206a13b2f233898c78359b21c8226024d0a7a93df5eb6c282bdbf005a4aab497e096f2847")
                .append(
                    "76c71cee57932a8fb89f6d6b8743b60a4ea374899a94a2e0f218d5c55818cefb1790c8529a76dba31ebb0f4592d709b49587d2317970d39c086f18dd244291d9")
                .append(
                    "eedb16705e53e3350591bd4ff4566a3595ac0f0ce24b5e112a3d033bc51b6fea0a92296dea7f5e20bf6ee6bc347d868fda193c395b9bb147e55e5a9f67cfe741")
                .append(
                    "7eea7d699b155bd13804204df7ea91fa9249e4474dddf35188f77019c67d201e4c10d7079c5ad492a71afff9a23ca7e900ba7d1bdeaf3270514d8eb35eab8a0a")
                .append(
                    "718bb7273aeb37768fa589ed8ab01fbf4027f4ebdbbae128d21e485f061c20183a9bc2e31edbda0727442e9d58eb0fe198440fe199e02e77c0f7b99973f1f74c")
                .append(
                    "c9089a51ab96c94a84d66e6aa48b2d0a4543adb5a789039a2aa7b335ca85c91026c7d3c894da53ae364188c3fd92f78e01d080399884a47385aa792e38150cda")
                .append(
                    "a8620b2ebeca41fbc773bb837b5e724d6eb2de570d99858df0d7d97067fb8103b21757873b735097b35d3bea8fd1c359a9e8a63c1540c76c9784cf8d975e995c")
                .append(
                    "778401b94a2e66e6993ad67ad3ecdc2acb17779f1ea8606827ec92b11c728f8c3b6d3f04a3e6ed05ff81dd76d5dc5695a50377bc135aaf1671cf68b750315493")
                .append(
                    "6c64510164d53312bf3c41740c7a237b05faf4a191bd8a95dafa068dbcf370255c725900ce5c934f36feadcfe55b687c440574c1f06f39d207a8553d39156a24")
                .append(
                    "845f64fd8324bb85312979dead74f764c9677aab89801ad4f927f1c00f12e28f22422bb44200d1969d9ab377dd6b099dc6dbc3222e9321b2c1e84f8e2f07731c")
                .toString());
  }
}
