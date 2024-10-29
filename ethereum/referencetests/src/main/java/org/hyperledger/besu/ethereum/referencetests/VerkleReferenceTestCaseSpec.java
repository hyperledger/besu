/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.referencetests;

import static org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider.createVerkleInMemoryWorldStateArchive;

import org.apache.commons.lang3.NotImplementedException;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ParsedExtraData;
import org.hyperledger.besu.ethereum.core.Request;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VerkleReferenceTestCaseSpec implements BlockchainReferenceTestCase {

  private final String network;

  private final CandidateBlock[] candidateBlocks;

  private final ReferenceTestBlockHeader genesisBlockHeader;

  private final Hash lastBlockHash;

  private final WorldStateArchive worldStateArchive;

  private final MutableBlockchain blockchain;
  private final String sealEngine;

  private final ProtocolContext protocolContext;

  private static WorldStateArchive buildWorldStateArchive(
      final Map<String, ReferenceTestWorldState.AccountMock> accounts,
      final MutableBlockchain blockchain,
      final BlockHeader genesis) {
    final WorldStateArchive worldStateArchive = createVerkleInMemoryWorldStateArchive(blockchain);

    final MutableWorldState worldState = worldStateArchive.getMutable();
    final WorldUpdater updater = worldState.updater();

    for (final Map.Entry<String, ReferenceTestWorldState.AccountMock> entry : accounts.entrySet()) {
      ReferenceTestWorldState.insertAccount(
          updater, Address.fromHexString(entry.getKey()), entry.getValue());
    }
    updater.commit();

    worldState.persist(genesis);

    return worldStateArchive;
  }

  private static MutableBlockchain buildBlockchain(final BlockHeader genesisBlockHeader) {
    final Block genesisBlock = new Block(genesisBlockHeader, BlockBody.empty());
    return InMemoryKeyValueStorageProvider.createInMemoryBlockchain(genesisBlock);
  }

  @JsonCreator
  public VerkleReferenceTestCaseSpec(
      @JsonProperty("network") final String network,
      @JsonProperty("blocks") final CandidateBlock[] candidateBlocks,
      @JsonProperty("genesisBlockHeader") final ReferenceTestBlockHeader genesisBlockHeader,
      @SuppressWarnings("unused") @JsonProperty("genesisRLP") final String genesisRLP,
      @JsonProperty("pre") final Map<String, ReferenceTestWorldState.AccountMock> accounts,
      @JsonProperty("lastblockhash") final String lastBlockHash,
      @JsonProperty("sealEngine") final String sealEngine) {
    this.network = network;
    this.candidateBlocks = candidateBlocks;
    this.genesisBlockHeader = genesisBlockHeader;
    this.lastBlockHash = Hash.fromHexString(lastBlockHash);
    this.blockchain = buildBlockchain(genesisBlockHeader);
    this.worldStateArchive = buildWorldStateArchive(accounts, this.blockchain, genesisBlockHeader);
    this.sealEngine = sealEngine;
    this.protocolContext =
        new ProtocolContext(this.blockchain, this.worldStateArchive, null, new BadBlockManager());
  }

  @Override
  public String getNetwork() {
    return network;
  }

  @Override
  public List<Block> getBlocks() {
    return Arrays.stream(candidateBlocks).map(CandidateBlock::getBlock).toList();
  }

  @Override
  public WorldStateArchive getWorldStateArchive() {
    return worldStateArchive;
  }

  @Override
  public BlockHeader getGenesisBlockHeader() {
    return genesisBlockHeader;
  }

  @Override
  public MutableBlockchain getBlockchain() {
    return blockchain;
  }

  @Override
  public ProtocolContext getProtocolContext() {
    return protocolContext;
  }

  @Override
  public Hash getLastBlockHash() {
    return lastBlockHash;
  }

  @Override
  public boolean isExecutable(final Block block) {
    Optional<CandidateBlock> candidateBlock =
        Arrays.stream(candidateBlocks)
            .filter(cb -> Objects.equals(cb.getBlock(), block))
            .findFirst();
    return candidateBlock.isPresent() && candidateBlock.get().rlp != null;
  }

  @Override
  public boolean isValid(final Block block) {
    Optional<CandidateBlock> candidateBlock =
        Arrays.stream(candidateBlocks)
            .filter(cb -> Objects.equals(cb.getBlock(), block))
            .findFirst();
    return candidateBlock.isPresent() && candidateBlock.get().valid;
  }

  @Override
  public boolean areAllTransactionsValid(final Block block) {
    throw new NotImplementedException("areAllTransactionsValid not available for verkle test");
  }

  @Override
  public String getSealEngine() {
    return sealEngine;
  }

  public static class ReferenceTestBlockHeader extends BlockHeader {

    @JsonCreator
    public ReferenceTestBlockHeader(
        @JsonProperty("parentHash") final String parentHash,
        @JsonProperty("uncleHash") final String uncleHash,
        @JsonProperty("coinbase") final String coinbase,
        @JsonProperty("stateRoot") final String stateRoot,
        @JsonProperty("transactionsTrie") final String transactionsTrie,
        @JsonProperty("receiptTrie") final String receiptTrie,
        @JsonProperty("bloom") final String bloom,
        @JsonProperty("difficulty") final String difficulty,
        @JsonProperty("number") final String number,
        @JsonProperty("gasLimit") final String gasLimit,
        @JsonProperty("gasUsed") final String gasUsed,
        @JsonProperty("timestamp") final String timestamp,
        @JsonProperty("extraData") final String extraData,
        @JsonProperty("baseFeePerGas") final String baseFee,
        @JsonProperty("mixHash") final String mixHash,
        @JsonProperty("nonce") final String nonce,
        @JsonProperty("withdrawalsRoot") final String withdrawalsRoot,
        @JsonProperty("hash") final String hash) {
      super(
          Hash.fromHexString(parentHash), // parentHash
          uncleHash == null ? Hash.EMPTY_LIST_HASH : Hash.fromHexString(uncleHash), // ommersHash
          Address.fromHexString(coinbase), // coinbase
          Hash.fromHexString(stateRoot), // stateRoot
          transactionsTrie == null
              ? Hash.EMPTY_TRIE_HASH
              : Hash.fromHexString(transactionsTrie), // transactionsRoot
          receiptTrie == null
              ? Hash.EMPTY_TRIE_HASH
              : Hash.fromHexString(receiptTrie), // receiptTrie
          LogsBloomFilter.fromHexString(bloom), // bloom
          Difficulty.fromHexString(difficulty), // difficulty
          Long.decode(number), // number
          Long.decode(gasLimit), // gasLimit
          Long.decode(gasUsed), // gasUsed
          Long.decode(timestamp), // timestamp
          Bytes.fromHexString(extraData), // extraData
          baseFee != null ? Wei.fromHexString(baseFee) : null, // baseFee
          Hash.fromHexString(mixHash), // mixHash
          Bytes.fromHexStringLenient(nonce).toLong(),
          withdrawalsRoot != null ? Hash.fromHexString(withdrawalsRoot) : null,
          0L,
          null,
          null,
          null,
          null,
          null,
          new BlockHeaderFunctions() {
            @Override
            public Hash hash(final BlockHeader header) {
              return hash == null ? null : Hash.fromHexString(hash);
            }

            @Override
            public ParsedExtraData parseExtraData(final BlockHeader header) {
              return null;
            }
          });
    }
  }

  @JsonIgnoreProperties({
    "blocknumber",
    "chainname",
    "chainnetwork",
    "expectException",
    "expectExceptionByzantium",
    "expectExceptionConstantinople",
    "expectExceptionConstantinopleFix",
    "expectExceptionIstanbul",
    "expectExceptionEIP150",
    "expectExceptionEIP158",
    "expectExceptionFrontier",
    "expectExceptionHomestead",
    "expectExceptionALL",
    "hasBigInt",
    "rlp_decoded",
    "transactionSequence"
  })
  public static class CandidateBlock {

    private final Bytes rlp;

    private final Boolean valid;

    @JsonCreator
    public CandidateBlock(
        @JsonProperty("rlp") final String rlp,
        @JsonProperty("blockHeader") final ReferenceTestBlockHeader blockHeader,
        @JsonProperty("transactions") final Object transactions,
        @JsonProperty("uncleHeaders") final Object uncleHeaders,
        @JsonProperty("withdrawals") final Object withdrawals,
        @SuppressWarnings("unused") @JsonProperty("witness") final Object witness) {
      boolean blockValid = true;
      // The BLOCK__WrongCharAtRLP_0 test has an invalid character in its rlp string.
      Bytes rlpAttempt = null;
      try {
        rlpAttempt = Bytes.fromHexString(rlp);
      } catch (final IllegalArgumentException e) {
        blockValid = false;
      }
      this.rlp = rlpAttempt;

      if (blockHeader == null
          && transactions == null
          && uncleHeaders == null
          && withdrawals == null) {
        blockValid = false;
      }

      this.valid = blockValid;
    }

    public Block getBlock() {
      final RLPInput input = new BytesValueRLPInput(rlp, false);
      input.enterList();
      final MainnetBlockHeaderFunctions blockHeaderFunctions = new MainnetBlockHeaderFunctions();
      final BlockHeader header = BlockHeader.readFrom(input, blockHeaderFunctions);
      final BlockBody body =
              new BlockBody(
                      input.readList(Transaction::readFrom),
                      input.readList(inputData -> BlockHeader.readFrom(inputData, blockHeaderFunctions)),
                      input.isEndOfCurrentList()
                              ? Optional.empty()
                              : Optional.of(input.readList(Withdrawal::readFrom)));
      return new Block(header, body);
    }
  }
}
