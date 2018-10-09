package net.consensys.pantheon.ethereum.jsonrpc.internal.processor;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.MutableWorldState;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.debug.TraceFrame;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.ethereum.mainnet.TransactionProcessor;
import net.consensys.pantheon.ethereum.mainnet.TransactionProcessor.Result;
import net.consensys.pantheon.ethereum.vm.DebugOperationTracer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransactionTracerTest {

  @Mock private ProtocolSchedule<Void> protocolSchedule;
  @Mock private Blockchain blockchain;

  @Mock private WorldStateArchive worldStateArchive;

  @Mock private BlockHeader blockHeader;

  @Mock private BlockBody blockBody;

  @Mock private BlockHeader previousBlockHeader;

  @Mock private Transaction transaction;

  @Mock private Transaction otherTransaction;

  @Mock private DebugOperationTracer tracer;

  @Mock private ProtocolSpec<Void> protocolSpec;

  @Mock private MutableWorldState mutableWorldState;

  @Mock private TransactionProcessor transactionProcessor;

  private TransactionTracer transactionTracer;

  private final Hash transactionHash =
      Hash.fromHexString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  private final Hash otherTransactionHash =
      Hash.fromHexString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
  private final Hash blockHash =
      Hash.fromHexString("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");

  private final Hash previousBlockHash =
      Hash.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

  private final Hash invalidBlockHash =
      Hash.fromHexString("1111111111111111111111111111111111111111111111111111111111111111");

  @Before
  public void setUp() throws Exception {
    transactionTracer =
        new TransactionTracer(new BlockReplay(protocolSchedule, blockchain, worldStateArchive));
    when(transaction.hash()).thenReturn(transactionHash);
    when(otherTransaction.hash()).thenReturn(otherTransactionHash);
    when(blockHeader.getNumber()).thenReturn(12L);
    when(blockHeader.getHash()).thenReturn(blockHash);
    when(blockHeader.getParentHash()).thenReturn(previousBlockHash);
    when(previousBlockHeader.getStateRoot()).thenReturn(Hash.ZERO);
    when(worldStateArchive.getMutable(Hash.ZERO)).thenReturn(mutableWorldState);
    when(protocolSchedule.getByBlockNumber(12)).thenReturn(protocolSpec);
    when(protocolSpec.getTransactionProcessor()).thenReturn(transactionProcessor);
    when(protocolSpec.getMiningBeneficiaryCalculator()).thenReturn(BlockHeader::getCoinbase);
  }

  @Test
  public void traceTransactionShouldReturnNoneWhenBlockHeaderNotFound() {
    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(invalidBlockHash, transactionHash, tracer);
    assertEquals(Optional.empty(), transactionTrace);
  }

  @Test
  public void traceTransactionShouldReturnTraceFramesFromExecutionTracer() {
    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(previousBlockHash)).thenReturn(Optional.of(previousBlockHeader));
    when(blockBody.getTransactions()).thenReturn(Collections.singletonList(transaction));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));
    final List<TraceFrame> traceFrames = Collections.singletonList(mock(TraceFrame.class));
    when(tracer.getTraceFrames()).thenReturn(traceFrames);

    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(blockHash, transactionHash, tracer);

    assertEquals(traceFrames, transactionTrace.get().getTraceFrames());
  }

  @Test
  public void
      traceTransactionShouldReturnTraceFramesFromExecutionTracerAfterExecutingOtherTransactions() {
    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(previousBlockHash)).thenReturn(Optional.of(previousBlockHeader));

    when(blockBody.getTransactions()).thenReturn(Arrays.asList(otherTransaction, transaction));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));
    final List<TraceFrame> traceFrames = Collections.singletonList(mock(TraceFrame.class));
    when(tracer.getTraceFrames()).thenReturn(traceFrames);

    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(blockHash, transactionHash, tracer);

    assertEquals(traceFrames, transactionTrace.get().getTraceFrames());
  }

  @Test
  public void traceTransactionShouldReturnResultFromProcessTransaction() {
    final Result result = mock(Result.class);

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(previousBlockHash)).thenReturn(Optional.of(previousBlockHeader));

    when(blockBody.getTransactions()).thenReturn(Collections.singletonList(transaction));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));

    when(transactionProcessor.processTransaction(
            blockchain,
            mutableWorldState.updater(),
            blockHeader,
            transaction,
            blockHeader.getCoinbase(),
            tracer))
        .thenReturn(result);

    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(blockHash, transactionHash, tracer);

    assertEquals(result, transactionTrace.get().getResult());
  }

  @Test
  public void traceTransactionShouldReturnEmptyResultWhenTransactionNotInCurrentBlock() {

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockHeader(previousBlockHash)).thenReturn(Optional.of(previousBlockHeader));

    when(blockBody.getTransactions()).thenReturn(Collections.singletonList(otherTransaction));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.of(blockBody));

    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(blockHash, transactionHash, tracer);

    assertEquals(Optional.empty(), transactionTrace);
  }

  @Test
  public void traceTransactionShouldReturnEmptyResultWhenBlockIsNotAvailable() {

    when(blockchain.getBlockHeader(blockHash)).thenReturn(Optional.of(blockHeader));
    when(blockchain.getBlockBody(blockHash)).thenReturn(Optional.empty());

    final Optional<TransactionTrace> transactionTrace =
        transactionTracer.traceTransaction(blockHash, transactionHash, tracer);

    assertEquals(Optional.empty(), transactionTrace);
  }
}
