package net.consensys.pantheon.ethereum.mainnet;

import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.util.bytes.Bytes32;

import java.io.IOException;
import java.util.Arrays;

import org.assertj.core.api.Assertions;
import org.junit.Test;

/** Tests for {@link BodyValidation}. */
public final class BodyValidationTest {

  @Test
  public void calculateTransactionsRoot() throws IOException {
    for (final int block : Arrays.asList(300006, 4400002)) {
      final BlockHeader header = ValidationTestUtils.readHeader(block);
      final BlockBody body = ValidationTestUtils.readBody(block);
      final Bytes32 transactionRoot = BodyValidation.transactionsRoot(body.getTransactions());
      Assertions.assertThat(header.getTransactionsRoot()).isEqualTo(transactionRoot);
    }
  }

  @Test
  public void calculateOmmersHash() throws IOException {
    for (final int block : Arrays.asList(300006, 4400002)) {
      final BlockHeader header = ValidationTestUtils.readHeader(block);
      final BlockBody body = ValidationTestUtils.readBody(block);
      final Bytes32 ommersHash = BodyValidation.ommersHash(body.getOmmers());
      Assertions.assertThat(header.getOmmersHash()).isEqualTo(ommersHash);
    }
  }
}
