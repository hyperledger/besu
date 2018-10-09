package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.rlp.BytesValueRLPInput;
import net.consensys.pantheon.ethereum.rlp.RLPInput;
import net.consensys.pantheon.ethereum.util.ByteArrayUtil;
import net.consensys.pantheon.util.bytes.BytesValue;

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
