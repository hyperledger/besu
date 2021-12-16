package org.hyperledger.besu.consensus.merge.blockcreation;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.plugin.data.Address;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MergeCoordinatorTest {

    private MergeCoordinator mc;
    private final ProtocolContext protocolContext = mock(ProtocolContext.class);
    private final ProtocolSchedule protocolSchedule = mock(ProtocolSchedule.class);
    private final AbstractPendingTransactionsSorter transactionsSorter = mock(AbstractPendingTransactionsSorter.class);
    private final ProtocolSpec protocolSpec = mock(ProtocolSpec.class);
    private final MiningParameters miningParameters = mock(MiningParameters.class);
    private final BlockValidator blockValidator = mock(BlockValidator.class);
    private final MutableBlockchain blockchain = mock(MutableBlockchain.class);
    private final Hash TERMINAL_BLOCK_HASH = Hash.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000");
    private final long TERMINAL_BLOCK_NUMBER = 14000000L;

    @Before
    public void setUp() {
        when(protocolContext.getBlockchain()).thenReturn(blockchain);
        BadBlockManager bbm = mock(BadBlockManager.class);
        when(protocolSpec.getBadBlocksManager()).thenReturn(bbm);
        when(protocolSchedule.getByBlockNumber(anyLong())).thenReturn(protocolSpec);

        this.mc = new MergeCoordinator(protocolContext,protocolSchedule,transactionsSorter,miningParameters,blockValidator);
        BlockHeader terminalBlockHeader = mock(BlockHeader.class);
        when(terminalBlockHeader.getBlockHash()).thenReturn(TERMINAL_BLOCK_HASH);
        when(terminalBlockHeader.getNumber()).thenReturn(TERMINAL_BLOCK_NUMBER);
        //Block terminalBlock = new Block(terminalBlockHeader, BlockBody.empty());
        //this.blockchain.appendBlock(terminalBlock, Collections.emptyList());
        when(blockchain.getBlockHeader(TERMINAL_BLOCK_HASH)).thenReturn(Optional.of(terminalBlockHeader));


    }

    @Test
    public void latestValidAncestorDescendsFromTerminal() {
        BlockHeader parentHeader = mock(BlockHeader.class);
        when(parentHeader.getParentHash()).thenReturn(TERMINAL_BLOCK_HASH);
        when(parentHeader.getBlockHash()).thenReturn(Hash.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001"));
        when(parentHeader.getNumber()).thenReturn(TERMINAL_BLOCK_NUMBER+1);
        //Block parent = new Block(parentHeader, BlockBody.empty());
        when(blockchain.getBlockHeader(Hash.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001"))).thenReturn(Optional.of(parentHeader));

        BlockHeader childHeader = mock(BlockHeader.class);
        when(childHeader.getParentHash()).thenReturn(Hash.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000001"));
        when(childHeader.getBlockHash()).thenReturn(Hash.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000002"));
        when(childHeader.getNumber()).thenReturn(TERMINAL_BLOCK_NUMBER+2);
        Block child = new Block(childHeader, BlockBody.empty());
        when(blockchain.getBlockHeader(Hash.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000002"))).thenReturn(Optional.of(childHeader));

        assertThat(this.mc.latestValidAncestorDescendsFromTerminal(child)).isTrue();
    }
}