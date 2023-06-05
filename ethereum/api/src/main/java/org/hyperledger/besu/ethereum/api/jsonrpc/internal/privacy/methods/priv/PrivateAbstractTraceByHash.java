package org.hyperledger.besu.ethereum.api.jsonrpc.internal.privacy.methods.priv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.flat.FlatTraceGenerator;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.debug.TraceOptions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class PrivateAbstractTraceByHash implements JsonRpcMethod   {

    protected final Supplier<BlockTracer> blockTracerSupplier;
    protected final BlockchainQueries blockchainQueries;
    protected final ProtocolSchedule protocolSchedule;

    protected PrivateAbstractTraceByHash(final Supplier<BlockTracer> blockTracerSupplier, final BlockchainQueries blockchainQueries, final ProtocolSchedule protocolSchedule) {
        this.blockTracerSupplier = blockTracerSupplier;
        this.blockchainQueries = blockchainQueries;
        this.protocolSchedule = protocolSchedule;
    }

    public Stream<FlatTrace> resultByTransactionHash(final Hash transactionHash) {
        return blockchainQueries
                .transactionByHash(transactionHash)
                .flatMap(TransactionWithMetadata::getBlockNumber)
                .flatMap(blockNumber -> blockchainQueries.getBlockchain().getBlockByNumber(blockNumber))
                .map(block -> getTraceBlock(block, transactionHash))
                .orElse(Stream.empty());
    }

    private Stream<FlatTrace> getTraceBlock(final Block block, final Hash transactionHash) {
        if (block == null || block.getBody().getTransactions().isEmpty()) {
            return Stream.empty();
        }
        return Tracer.processTracing(
                        blockchainQueries,
                        Optional.of(block.getHeader()),
                        mutableWorldState -> {
                            final TransactionTrace transactionTrace = getTransactionTrace(block, transactionHash);
                            return Optional.ofNullable(getTraceStream(transactionTrace, block));
                        })
                .orElse(Stream.empty());
    }

    private TransactionTrace getTransactionTrace(final Block block, final Hash transactionHash) {
        return Tracer.processTracing(
                        blockchainQueries,
                        Optional.of(block.getHeader()),
                        mutableWorldState -> {
                            return blockTracerSupplier
                                    .get()
                                    .trace(
                                            mutableWorldState,
                                            block,
                                            new DebugOperationTracer(new TraceOptions(false, false, true)))
                                    .map(BlockTrace::getTransactionTraces)
                                    .orElse(Collections.emptyList())
                                    .stream()
                                    .filter(trxTrace -> trxTrace.getTransaction().getHash().equals(transactionHash))
                                    .findFirst();
                        })
                .orElseThrow();
    }

    private Stream<FlatTrace> getTraceStream(
            final TransactionTrace transactionTrace, final Block block) {
        return FlatTraceGenerator.generateFromTransactionTraceAndBlock(
                        this.protocolSchedule, transactionTrace, block)
                .map(FlatTrace.class::cast);
    }

    protected JsonNode arrayNodeFromTraceStream(final Stream<FlatTrace> traceStream) {
        final ObjectMapper mapper = new ObjectMapper();
        final ArrayNode resultArrayNode = mapper.createArrayNode();
        traceStream.forEachOrdered(resultArrayNode::addPOJO);
        return resultArrayNode;
    }
}
