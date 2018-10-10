package net.consensys.pantheon.util;

import static net.consensys.pantheon.controller.KeyPairUtil.loadKeyPair;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.controller.MainnetPantheonController;
import net.consensys.pantheon.controller.PantheonController;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.blockcreation.EthHashBlockMiner;
import net.consensys.pantheon.ethereum.blockcreation.MiningParameters;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.core.MiningParametersTestBuilder;
import net.consensys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.util.uint.UInt256;

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
  public void blockImport() throws Exception {
    final URL importFileURL =
        getClass()
            .getClassLoader()
            .getResource("net/consensys/pantheon/ethereum/jsonrpc/json-rpc-test.bin");
    assertThat(importFileURL).isNotNull();

    final Path source = new File(importFileURL.toURI()).toPath();
    final Path target = folder.newFolder().toPath();

    final URL genesisJsonUrl =
        getClass()
            .getClassLoader()
            .getResource("net/consensys/pantheon/ethereum/jsonrpc/jsonRpcTestGenesis.json");
    assertThat(genesisJsonUrl).isNotNull();
    final String genesisJson = Resources.toString(genesisJsonUrl, Charsets.UTF_8);
    final KeyPair keyPair = loadKeyPair(target);

    final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();
    final MiningParameters miningParams = new MiningParametersTestBuilder().enabled(false).build();
    final GenesisConfig<Void> genesisConfig = GenesisConfig.fromJson(genesisJson, protocolSchedule);
    final PantheonController<Void, EthHashBlockMiner> ctrl =
        MainnetPantheonController.init(
            target,
            genesisConfig,
            SynchronizerConfiguration.builder().build(),
            miningParams,
            10,
            keyPair);
    final BlockchainImporter.ImportResult result =
        blockImporter.importBlockchain(source, ctrl, true, 1, 1, false, false, null);
    System.out.println(source);
    System.out.println(target);

    System.out.println(result);
    assertThat(result.count).isEqualTo(33);
    assertThat(result.td).isEqualTo(UInt256.of(4357120));
  }
}
