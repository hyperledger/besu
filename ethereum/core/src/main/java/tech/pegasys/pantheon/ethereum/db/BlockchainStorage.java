package tech.pegasys.pantheon.ethereum.db;

import tech.pegasys.pantheon.ethereum.chain.TransactionLocation;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BlockchainStorage {

  Optional<Hash> getChainHead();

  Collection<Hash> getForkHeads();

  Optional<BlockHeader> getBlockHeader(Hash blockHash);

  Optional<BlockBody> getBlockBody(Hash blockHash);

  Optional<List<TransactionReceipt>> getTransactionReceipts(Hash blockHash);

  Optional<Hash> getBlockHash(long blockNumber);

  Optional<UInt256> getTotalDifficulty(Hash blockHash);

  Optional<TransactionLocation> getTransactionLocation(Hash transactionHash);

  Updater updater();

  interface Updater {

    void putBlockHeader(Hash blockHash, BlockHeader blockHeader);

    void putBlockBody(Hash blockHash, BlockBody blockBody);

    void putTransactionLocation(Hash transactionHash, TransactionLocation transactionLocation);

    void putTransactionReceipts(Hash blockHash, List<TransactionReceipt> transactionReceipts);

    void putBlockHash(long blockNumber, Hash blockHash);

    void putTotalDifficulty(Hash blockHash, UInt256 totalDifficulty);

    void setChainHead(Hash blockHash);

    void setForkHeads(Collection<Hash> forkHeadHashes);

    void removeBlockHash(long blockNumber);

    void removeTransactionLocation(Hash transactionHash);

    void commit();

    void rollback();
  }
}
