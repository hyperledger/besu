package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Gas;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.debug.TraceFrame;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.processor.TransactionTrace;
import net.consensys.pantheon.ethereum.jsonrpc.internal.processor.TransactionTracer;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import net.consensys.pantheon.ethereum.jsonrpc.internal.queries.TransactionWithMetadata;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.DebugTraceTransactionResult;
import net.consensys.pantheon.ethereum.jsonrpc.internal.results.StructLog;
import net.consensys.pantheon.ethereum.mainnet.TransactionProcessor.Result;
import net.consensys.pantheon.ethereum.vm.ExceptionalHaltReason;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

public class DebugTraceTransactionTest {

  private final JsonRpcParameter parameters = new JsonRpcParameter();
  private final BlockchainQueries blockchain = mock(BlockchainQueries.class);
  private final TransactionTracer transactionTracer = mock(TransactionTracer.class);
  private final DebugTraceTransaction debugTraceTransaction =
      new DebugTraceTransaction(blockchain, transactionTracer, parameters);
  private final Transaction transaction = mock(Transaction.class);

  private final BlockHeader blockHeader = mock(BlockHeader.class);
  private final Hash blockHash =
      Hash.fromHexString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  private final Hash transactionHash =
      Hash.fromHexString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

  @Test
  public void nameShouldBeDebugTraceTransaction() {
    assertEquals("debug_traceTransaction", debugTraceTransaction.getName());
  }

  @Test
  public void shouldTraceTheTransactionUsingTheTransactionTracer() {
    final TransactionWithMetadata transactionWithMetadata =
        new TransactionWithMetadata(transaction, 12L, blockHash, 2);
    final Map<String, Boolean> map = new HashMap<>();
    map.put("disableStorage", true);
    final Object[] params = new Object[] {transactionHash, map};
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "debug_traceTransaction", params);
    final Result result = mock(Result.class);

    final TraceFrame traceFrame =
        new TraceFrame(
            12,
            "NONE",
            Gas.of(45),
            Optional.of(Gas.of(56)),
            2,
            EnumSet.noneOf(ExceptionalHaltReason.class),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    final List<TraceFrame> traceFrames = Collections.singletonList(traceFrame);
    final TransactionTrace transactionTrace =
        new TransactionTrace(transaction, result, traceFrames);
    when(transaction.getGasLimit()).thenReturn(100L);
    when(result.getGasRemaining()).thenReturn(27L);
    when(result.getOutput()).thenReturn(BytesValue.fromHexString("1234"));
    when(blockHeader.getNumber()).thenReturn(12L);
    when(blockchain.headBlockNumber()).thenReturn(12L);
    when(blockchain.transactionByHash(transactionHash))
        .thenReturn(Optional.of(transactionWithMetadata));
    when(transactionTracer.traceTransaction(eq(blockHash), eq(transactionHash), any()))
        .thenReturn(Optional.of(transactionTrace));
    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) debugTraceTransaction.response(request);
    final DebugTraceTransactionResult transactionResult =
        (DebugTraceTransactionResult) response.getResult();

    assertEquals(73, transactionResult.getGas());
    assertEquals("1234", transactionResult.getReturnValue());
    final List<StructLog> expectedStructLogs = Collections.singletonList(new StructLog(traceFrame));
    assertEquals(expectedStructLogs, transactionResult.getStructLogs());
  }
}
