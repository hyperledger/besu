package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.ExecutionContextTestFixture;
import tech.pegasys.pantheon.ethereum.core.PendingTransactions;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;

import com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class EthHashBlockCreatorTest {

  private final Address BLOCK_1_COINBASE =
      Address.fromHexString("0x05a56e2d52c817161883f50c441c3228cfe54d9f");

  private static final long BLOCK_1_TIMESTAMP = Long.parseUnsignedLong("55ba4224", 16);

  private static final long BLOCK_1_NONCE = Long.parseLong("539bd4979fef1ec4", 16);

  private static final BytesValue BLOCK_1_EXTRA_DATA =
      BytesValue.fromHexString("0x476574682f76312e302e302f6c696e75782f676f312e342e32");

  private final ExecutionContextTestFixture executionContextTestFixture =
      new ExecutionContextTestFixture();

  @Test
  public void createMainnetBlock1() throws IOException {
    final EthHashSolver solver =
        new EthHashSolver(Lists.newArrayList(BLOCK_1_NONCE), new EthHasher.Light());
    final EthHashBlockCreator blockCreator =
        new EthHashBlockCreator(
            BLOCK_1_COINBASE,
            parent -> BLOCK_1_EXTRA_DATA,
            new PendingTransactions(1),
            executionContextTestFixture.getProtocolContext(),
            executionContextTestFixture.getProtocolSchedule(),
            gasLimit -> gasLimit,
            solver,
            Wei.ZERO,
            executionContextTestFixture.getBlockchain().getChainHeadHeader());

    // A Hashrate should not exist in the block creator prior to creating a block
    Assertions.assertThat(blockCreator.getHashesPerSecond().isPresent()).isFalse();

    final Block actualBlock = blockCreator.createBlock(BLOCK_1_TIMESTAMP);
    final Block expectedBlock = ValidationTestUtils.readBlock(1);

    Assertions.assertThat(actualBlock).isEqualTo(expectedBlock);
    Assertions.assertThat(blockCreator.getHashesPerSecond().isPresent()).isTrue();
  }
}
