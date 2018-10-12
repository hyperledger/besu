package tech.pegasys.pantheon.ethereum.mainnet;

import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.IOException;
import java.util.List;

import com.google.common.io.Resources;

public final class ValidationTestUtils {

  public static BlockHeader readHeader(final long num) throws IOException {
    final RLPInput input =
        new BytesValueRLPInput(
            BytesValue.wrap(
                Resources.toByteArray(
                    EthHashTest.class.getResource(String.format("block_%d.blocks", num)))),
            false);
    input.enterList();
    return BlockHeader.readFrom(input, MainnetBlockHashFunction::createHash);
  }

  public static BlockBody readBody(final long num) throws IOException {
    final RLPInput input =
        new BytesValueRLPInput(
            BytesValue.wrap(
                Resources.toByteArray(
                    EthHashTest.class.getResource(String.format("block_%d.blocks", num)))),
            false);
    input.enterList();
    input.skipNext();
    final List<Transaction> transactions = input.readList(Transaction::readFrom);
    final List<BlockHeader> ommers =
        input.readList(rlp -> BlockHeader.readFrom(rlp, MainnetBlockHashFunction::createHash));
    return new BlockBody(transactions, ommers);
  }

  public static Block readBlock(final long num) throws IOException {
    final RLPInput input =
        new BytesValueRLPInput(
            BytesValue.wrap(
                Resources.toByteArray(
                    EthHashTest.class.getResource(String.format("block_%d.blocks", num)))),
            false);
    input.enterList();
    final BlockHeader header = BlockHeader.readFrom(input, MainnetBlockHashFunction::createHash);
    final List<Transaction> transactions = input.readList(Transaction::readFrom);
    final List<BlockHeader> ommers =
        input.readList(rlp -> BlockHeader.readFrom(rlp, MainnetBlockHashFunction::createHash));
    final BlockBody body = new BlockBody(transactions, ommers);
    return new Block(header, body);
  }
}
