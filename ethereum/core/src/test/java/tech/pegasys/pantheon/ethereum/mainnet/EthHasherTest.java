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
