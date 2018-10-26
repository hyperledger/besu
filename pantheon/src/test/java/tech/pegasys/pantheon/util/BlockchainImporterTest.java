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
package tech.pegasys.pantheon.util;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.pantheon.controller.KeyPairUtil.loadKeyPair;

import tech.pegasys.pantheon.controller.MainnetPantheonController;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.chain.GenesisConfig;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.MiningParametersTestBuilder;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link BlockchainImporter}. */
public final class BlockchainImporterTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  BlockchainImporter blockImporter = new BlockchainImporter();

  @Test
  public void importBlocksWithoutValidation() throws Exception {
    blockImportTest(true);
  }

  @Test
  public void importBlocksWithValidation() throws Exception {
    blockImportTest(false);
  }

  private void blockImportTest(final boolean skipValidation) throws Exception {
    final URL importFileURL =
        getClass().getClassLoader().getResource("tech/pegasys/pantheon/util/blockchain-import.bin");
    assertThat(importFileURL).isNotNull();

    final Path source = new File(importFileURL.toURI()).toPath();
    final Path target = folder.newFolder().toPath();

    final URL genesisJsonUrl =
        getClass()
            .getClassLoader()
            .getResource("tech/pegasys/pantheon/util/blockchain-import-genesis-file.json");
    assertThat(genesisJsonUrl).isNotNull();
    final String genesisJson = Resources.toString(genesisJsonUrl, Charsets.UTF_8);
    final KeyPair keyPair = loadKeyPair(target);

    final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();
    final MiningParameters miningParams = new MiningParametersTestBuilder().enabled(false).build();
    final GenesisConfig<Void> genesisConfig = GenesisConfig.fromJson(genesisJson, protocolSchedule);
    final PantheonController<Void> ctrl =
        MainnetPantheonController.init(
            target,
            genesisConfig,
            SynchronizerConfiguration.builder().build(),
            miningParams,
            keyPair);
    final BlockchainImporter.ImportResult result =
        blockImporter.importBlockchain(source, ctrl, skipValidation, 1, 1, false, false, null);
    System.out.println(source);
    System.out.println(target);

    System.out.println(result);
    assertThat(result.count).isEqualTo(33);
    assertThat(result.td).isEqualTo(UInt256.of(4357120));
  }
}
