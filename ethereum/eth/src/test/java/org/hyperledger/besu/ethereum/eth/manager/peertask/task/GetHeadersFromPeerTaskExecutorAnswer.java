package org.hyperledger.besu.ethereum.eth.manager.peertask.task;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResponseCode;
import org.hyperledger.besu.ethereum.eth.manager.peertask.PeerTaskExecutorResult;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GetHeadersFromPeerTaskExecutorAnswer implements Answer<PeerTaskExecutorResult<List<BlockHeader>>> {
    private final Blockchain otherBlockchain;

    public GetHeadersFromPeerTaskExecutorAnswer(final Blockchain otherBlockchain) {
        this.otherBlockchain = otherBlockchain;
    }

    @Override
    public PeerTaskExecutorResult<List<BlockHeader>> answer(final InvocationOnMock invocationOnMock) throws Throwable {
        GetHeadersFromPeerTask task =
                invocationOnMock.getArgument(0, GetHeadersFromPeerTask.class);
        List<BlockHeader> getHeadersFromPeerTaskResult = new ArrayList<>();
        BlockHeader initialHeader;
        if (task.getBlockHash() != null) {
            initialHeader = otherBlockchain.getBlockHeader(task.getBlockHash()).orElse(null);
        } else {
            initialHeader = otherBlockchain.getBlockHeader(task.getBlockNumber()).orElse(null);
        }
        getHeadersFromPeerTaskResult.add(initialHeader);

        if (initialHeader != null && task.getMaxHeaders() > 1) {
            if (task.getDirection() == GetHeadersFromPeerTask.Direction.FORWARD) {
                int skip = task.getSkip() + 1;
                long nextHeaderNumber = initialHeader.getNumber() + skip;
                long getLimit = nextHeaderNumber + ((task.getMaxHeaders() - 1) * skip);
                for (long i = nextHeaderNumber; i < getLimit; i += skip) {
                    Optional<BlockHeader> header = otherBlockchain.getBlockHeader(i);
                    if (header.isPresent()) {
                        getHeadersFromPeerTaskResult.add(header.get());
                    } else {
                        break;
                    }
                }

            } else {
                int skip = task.getSkip() + 1;
                long nextHeaderNumber = initialHeader.getNumber() - skip;
                long getLimit = nextHeaderNumber - ((task.getMaxHeaders() - 1) * skip);
                for (long i = initialHeader.getNumber() - 1; i > getLimit; i -= skip) {
                    Optional<BlockHeader> header = otherBlockchain.getBlockHeader(i);
                    if (header.isPresent()) {
                        getHeadersFromPeerTaskResult.add(header.get());
                    } else {
                        break;
                    }
                }
            }
        }

        return new PeerTaskExecutorResult<>(
                Optional.of(getHeadersFromPeerTaskResult),
                PeerTaskExecutorResponseCode.SUCCESS,
                null);
    }
}
