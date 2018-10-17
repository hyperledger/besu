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
package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.util.ByteArrayUtil;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import com.google.common.io.Resources;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link EthHasher}. */
public final class EthHasherTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  // TODO: Find a faster way to test HashimotoFull, this test takes almost 2 minutes.
  @Test
  @Ignore
  public void hashimotoFull() throws Exception {
    try (final EthHasher.Full hasher = new EthHasher.Full(folder.newFile().toPath())) {
      final RLPInput input =
          new BytesValueRLPInput(
              BytesValue.wrap(
                  Resources.toByteArray(EthHashTest.class.getResource("block_300005.blocks"))),
              false);
      input.enterList();
      final BlockHeader header = BlockHeader.readFrom(input, MainnetBlockHashFunction::createHash);
      final byte[] buffer = new byte[64];
      hasher.hash(buffer, header.getNonce(), header.getNumber(), EthHash.hashHeader(header));
      Assertions.assertThat(
              ByteArrayUtil.compare(buffer, 0, 32, header.getMixHash().extractArray(), 0, 32))
          .isEqualTo(0);
    }
  }
}
