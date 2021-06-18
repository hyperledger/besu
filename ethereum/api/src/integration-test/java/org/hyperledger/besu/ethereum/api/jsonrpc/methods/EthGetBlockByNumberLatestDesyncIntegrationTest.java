package org.hyperledger.besu.ethereum.api.jsonrpc.methods;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.BlockchainImporter;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcTestMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.testutil.BlockTestUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetBlockByNumberLatestDesyncIntegrationTest {

    private static JsonRpcMethod ethGetBlockNumber;
    private static Integer latestFullySyncdBlockNumber = 0;

    @BeforeClass
    public void setUpOnce() throws Exception {
        final String genesisJson = Resources.toString(BlockTestUtil.getTestGenesisUrl(), Charsets.UTF_8);
        final BlockchainImporter importer = new BlockchainImporter(BlockTestUtil.getTestBlockchainUrl(), genesisJson);
        final WorldStateArchive state = InMemoryKeyValueStorageProvider.createInMemoryWorldStateArchive();
        //TODO: run same test with coverage of Bonsai state?
        importer.getGenesisState().writeStateTo(state.getMutable());
        final MutableBlockchain blockchain = InMemoryKeyValueStorageProvider.createInMemoryBlockchain(
                importer.getGenesisBlock());
        final ProtocolContext ether = new ProtocolContext(blockchain, state, null);

        //how to setup importer/state/chain to reflect de-syncd?

        final JsonRpcTestMethodsFactory factory = new JsonRpcTestMethodsFactory(importer, blockchain, state, ether);
        this.ethGetBlockNumber = factory.methods().get("eth_getBlockByNumber");

    }

    @Test
    public void shouldReturnedLatestFullSynced() {

    }
}
