package net.consensys.pantheon.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.controller.MainnetPantheonController;
import net.consensys.pantheon.controller.PantheonController;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.core.MiningParametersTestBuilder;
import net.consensys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import net.consensys.pantheon.testutil.BlockTestUtil;
import net.consensys.pantheon.util.uint.UInt256;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.google.common.io.Resources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link BlockImporter}. */
public final class BlockImporterTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  BlockImporter blockImporter = new BlockImporter();

  @Test
  public void blockImport() throws IOException {
    final Path source = folder.newFile().toPath();
    final Path target = folder.newFolder().toPath();
    BlockTestUtil.write1000Blocks(source);
    final BlockImporter.ImportResult result =
        blockImporter.importBlockchain(source, MainnetPantheonController.mainnet(target));
    assertThat(result.count).isEqualTo(1000);
    assertThat(result.td).isEqualTo(UInt256.of(21991996248790L));
  }

  @Test
  public void ibftImport() throws IOException {
    final Path source = folder.newFile().toPath();
    final Path target = folder.newFolder().toPath();
    final String config = Resources.toString(Resources.getResource("ibft_genesis.json"), UTF_8);

    try {
      Files.write(
          source,
          Resources.toByteArray(Resources.getResource("ibft.blocks")),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    }

    final PantheonController<?, ?> controller =
        PantheonController.fromConfig(
            SynchronizerConfiguration.builder().build(),
            config,
            target,
            false,
            10,
            new MiningParametersTestBuilder().enabled(false).build(),
            KeyPair.generate());
    final BlockImporter.ImportResult result = blockImporter.importBlockchain(source, controller);

    assertThat(result.count).isEqualTo(959);
  }
}
