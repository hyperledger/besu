/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.vm;

import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;
import static tech.pegasys.pantheon.ethereum.vm.WorldStateMock.insertAccount;

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.LogsBloomFilter;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.ParsedExtraData;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetBlockHeaderFunctions;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties({"_info", "postState"})
public class BlockchainReferenceTestCaseSpec {

  private final String network;

  private final CandidateBlock[] candidateBlocks;

  private final BlockHeaderMock genesisBlockHeader;

  private final Hash lastBlockHash;

  private final WorldStateArchive worldStateArchive;

  private final MutableBlockchain blockchain;
  private final String sealEngine;

  private final ProtocolContext<Void> protocolContext;

  private static WorldStateArchive buildWorldStateArchive(
      final Map<String, WorldStateMock.AccountMock> accounts) {
    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();

    final MutableWorldState worldState = worldStateArchive.getMutable();
    final WorldUpdater updater = worldState.updater();

    for (final Map.Entry<String, WorldStateMock.AccountMock> entry : accounts.entrySet()) {
      insertAccount(updater, Address.fromHexString(entry.getKey()), entry.getValue());
    }

    updater.commit();
    worldState.persist();

    return worldStateArchive;
  }

  private static MutableBlockchain buildBlockchain(final BlockHeader genesisBlockHeader) {
    final Block genesisBlock = new Block(genesisBlockHeader, BlockBody.empty());
    return createInMemoryBlockchain(genesisBlock);
  }

  @JsonCreator
  public BlockchainReferenceTestCaseSpec(
      @JsonProperty("network") final String network,
      @JsonProperty("blocks") final CandidateBlock[] candidateBlocks,
      @JsonProperty("genesisBlockHeader") final BlockHeaderMock genesisBlockHeader,
      @SuppressWarnings("unused") @JsonProperty("genesisRLP") final String genesisRLP,
      @JsonProperty("pre") final Map<String, WorldStateMock.AccountMock> accounts,
      @JsonProperty("lastblockhash") final String lastBlockHash,
      @JsonProperty("sealEngine") final String sealEngine) {
    this.network = network;
    this.candidateBlocks = candidateBlocks;
    this.genesisBlockHeader = genesisBlockHeader;
    this.lastBlockHash = Hash.fromHexString(lastBlockHash);
    this.worldStateArchive = buildWorldStateArchive(accounts);
    this.blockchain = buildBlockchain(genesisBlockHeader);
    this.sealEngine = sealEngine;
    this.protocolContext = new ProtocolContext<>(this.blockchain, this.worldStateArchive, null);
  }

  public String getNetwork() {
    return network;
  }

  public CandidateBlock[] getCandidateBlocks() {
    return candidateBlocks;
  }

  public WorldStateArchive getWorldStateArchive() {
    return worldStateArchive;
  }

  public BlockHeader getGenesisBlockHeader() {
    return genesisBlockHeader;
  }

  public MutableBlockchain getBlockchain() {
    return blockchain;
  }

  public ProtocolContext<Void> getProtocolContext() {
    return protocolContext;
  }

  public Hash getLastBlockHash() {
    return lastBlockHash;
  }

  public String getSealEngine() {
    return sealEngine;
  }

  public static class BlockHeaderMock extends BlockHeader {

    @JsonCreator
    public BlockHeaderMock(
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
        @JsonProperty("mixHash") final String mixHash,
        @JsonProperty("nonce") final String nonce,
        @JsonProperty("hash") final String hash) {
      super(
          Hash.fromHexString(parentHash), // parentHash
          Hash.fromHexString(uncleHash), // ommersHash
          Address.fromHexString(coinbase), // coinbase
          Hash.fromHexString(stateRoot), // stateRoot
          Hash.fromHexString(transactionsTrie), // transactionsRoot
          Hash.fromHexString(receiptTrie), // receiptTrie
          LogsBloomFilter.fromHexString(bloom), // bloom
          UInt256.fromHexString(difficulty), // difficulty
          Long.decode(number), // number
          Long.decode(gasLimit), // gasLimit
          Long.decode(gasUsed), // gasUsed
          Long.decode(timestamp), // timestamp
          BytesValue.fromHexString(extraData), // extraData
          Hash.fromHexString(mixHash), // mixHash
          BytesValue.fromHexString(nonce).getLong(0),
          new BlockHeaderFunctions() {
            @Override
            public Hash hash(final BlockHeader header) {
              return Hash.fromHexString(hash);
            }

            @Override
            public ParsedExtraData parseExtraData(final BlockHeader header) {
              return null;
            }
          });
    }
  }

  @JsonIgnoreProperties({
    "expectExceptionByzantium",
    "expectExceptionConstantinople",
    "expectExceptionConstantinopleFix",
    "expectExceptionIstanbul",
    "expectExceptionEIP150",
    "expectExceptionEIP158",
    "expectExceptionFrontier",
    "expectExceptionHomestead",
    "blocknumber",
    "chainname",
    "expectExceptionALL",
    "chainnetwork"
  })
  public static class CandidateBlock {

    private final BytesValue rlp;

    private final Boolean valid;

    @JsonCreator
    public CandidateBlock(
        @JsonProperty("rlp") final String rlp,
        @JsonProperty("blockHeader") final Object blockHeader,
        @JsonProperty("transactions") final Object transactions,
        @JsonProperty("uncleHeaders") final Object uncleHeaders) {
      Boolean valid = true;
      // The BLOCK__WrongCharAtRLP_0 test has an invalid character in its rlp string.
      BytesValue rlpAttempt = null;
      try {
        rlpAttempt = BytesValue.fromHexString(rlp);
      } catch (final IllegalArgumentException e) {
        valid = false;
      }
      this.rlp = rlpAttempt;

      if (blockHeader == null && transactions == null && uncleHeaders == null) {
        valid = false;
      }

      this.valid = valid;
    }

    public boolean isValid() {
      return valid;
    }

    public boolean isExecutable() {
      return rlp != null;
    }

    public Block getBlock() {
      final RLPInput input = new BytesValueRLPInput(rlp, false);
      input.enterList();
      final MainnetBlockHeaderFunctions blockHeaderFunctions = new MainnetBlockHeaderFunctions();
      final BlockHeader header = BlockHeader.readFrom(input, blockHeaderFunctions);
      final BlockBody body =
          new BlockBody(
              input.readList(Transaction::readFrom),
              input.readList(rlp -> BlockHeader.readFrom(rlp, blockHeaderFunctions)));
      return new Block(header, body);
    }
  }
}
