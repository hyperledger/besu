package tech.pegasys.pantheon.ethereum.util;

import static org.assertj.core.api.Assertions.assertThat;
import static sun.security.krb5.Confounder.bytes;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderBuilder;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.LogsBloomFilter;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.db.DefaultMutableBlockchain;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import tech.pegasys.pantheon.ethereum.testutil.BlockDataGenerator;
import tech.pegasys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

import org.junit.Test;

public class BlockchainUtilTest {

  @Test
  public void shouldReturnIndexOfCommonBlockForAscendingOrder() {
    BlockDataGenerator blockDataGenerator = new BlockDataGenerator();

    BlockHeader genesisHeader =
        BlockHeaderBuilder.create()
            .parentHash(Hash.ZERO)
            .ommersHash(Hash.ZERO)
            .coinbase(Address.fromHexString("0x0000000000000000000000000000000000000000"))
            .stateRoot(Hash.ZERO)
            .transactionsRoot(Hash.ZERO)
            .receiptsRoot(Hash.ZERO)
            .logsBloom(new LogsBloomFilter(BytesValue.of(bytes(LogsBloomFilter.BYTE_SIZE))))
            .difficulty(UInt256.ZERO)
            .number(0L)
            .gasLimit(1L)
            .gasUsed(1L)
            .timestamp(Instant.now().truncatedTo(ChronoUnit.SECONDS).getEpochSecond())
            .extraData(Bytes32.wrap(bytes(Bytes32.SIZE)))
            .mixHash(Hash.ZERO)
            .nonce(0L)
            .blockHashFunction(MainnetBlockHashFunction::createHash)
            .buildBlockHeader();
    BlockBody genesisBody = new BlockBody(Collections.emptyList(), Collections.emptyList());
    Block genesisBlock = new Block(genesisHeader, genesisBody);

    KeyValueStorage kvStoreLocal = new InMemoryKeyValueStorage();
    KeyValueStorage kvStoreRemote = new InMemoryKeyValueStorage();

    DefaultMutableBlockchain blockchainLocal =
        new DefaultMutableBlockchain(
            genesisBlock, kvStoreLocal, MainnetBlockHashFunction::createHash);
    DefaultMutableBlockchain blockchainRemote =
        new DefaultMutableBlockchain(
            genesisBlock, kvStoreRemote, MainnetBlockHashFunction::createHash);

    // Common chain segment
    Block commonBlock = null;
    for (long i = 1; i <= 3; i++) {
      BlockDataGenerator.BlockOptions options =
          new BlockDataGenerator.BlockOptions()
              .setBlockNumber(i)
              .setParentHash(blockchainLocal.getBlockHashByNumber(i - 1).get());
      commonBlock = blockDataGenerator.block(options);
      List<TransactionReceipt> receipts = blockDataGenerator.receipts(commonBlock);
      blockchainLocal.appendBlock(commonBlock, receipts);
      blockchainRemote.appendBlock(commonBlock, receipts);
    }

    // Populate local chain
    for (long i = 4; i <= 9; i++) {
      BlockDataGenerator.BlockOptions optionsLocal =
          new BlockDataGenerator.BlockOptions()
              .setDifficulty(UInt256.ZERO) // differentiator
              .setBlockNumber(i)
              .setParentHash(blockchainLocal.getBlockHashByNumber(i - 1).get());
      Block blockLocal = blockDataGenerator.block(optionsLocal);
      List<TransactionReceipt> receiptsLocal = blockDataGenerator.receipts(blockLocal);
      blockchainLocal.appendBlock(blockLocal, receiptsLocal);
    }

    // Populate remote chain
    for (long i = 4; i <= 9; i++) {
      BlockDataGenerator.BlockOptions optionsRemote =
          new BlockDataGenerator.BlockOptions()
              .setDifficulty(UInt256.ONE)
              .setBlockNumber(i)
              .setParentHash(blockchainRemote.getBlockHashByNumber(i - 1).get());
      Block blockRemote = blockDataGenerator.block(optionsRemote);
      List<TransactionReceipt> receiptsRemote = blockDataGenerator.receipts(blockRemote);
      blockchainRemote.appendBlock(blockRemote, receiptsRemote);
    }

    // Create a list of headers...
    List<BlockHeader> headers = new ArrayList<>();
    for (long i = 0L; i < blockchainRemote.getChainHeadBlockNumber(); i++) {
      headers.add(blockchainRemote.getBlockHeader(i).get());
    }

    OptionalInt maybeAncestorNumber =
        BlockchainUtil.findHighestKnownBlockIndex(blockchainLocal, headers, true);

    assertThat(maybeAncestorNumber.getAsInt())
        .isEqualTo(Math.toIntExact(commonBlock.getHeader().getNumber()));
  }
}
