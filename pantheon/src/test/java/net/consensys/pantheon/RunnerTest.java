package net.consensys.pantheon;

import static net.consensys.pantheon.controller.KeyPairUtil.loadKeyPair;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.controller.MainnetPantheonController;
import net.consensys.pantheon.controller.PantheonController;
import net.consensys.pantheon.crypto.SECP256K1.KeyPair;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.blockcreation.EthHashBlockMiner;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockImporter;
import net.consensys.pantheon.ethereum.core.BlockSyncTestUtils;
import net.consensys.pantheon.ethereum.core.MiningParametersTestBuilder;
import net.consensys.pantheon.ethereum.eth.sync.SyncMode;
import net.consensys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import net.consensys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import net.consensys.pantheon.ethereum.mainnet.HeaderValidationMode;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.ethereum.p2p.peers.DefaultPeer;
import net.consensys.pantheon.util.uint.UInt256;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link Runner}. */
public final class RunnerTest {

  private static final int NETWORK_ID = 10;
  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void fullSyncFromGenesis() throws Exception {
    syncFromGenesis(SyncMode.FULL);
  }

  @Test
  public void fastSyncFromGenesis() throws Exception {
    syncFromGenesis(SyncMode.FAST);
  }

  private void syncFromGenesis(final SyncMode mode) throws Exception {
    final Path dbAhead = temp.newFolder().toPath();
    final int blockCount = 500;
    final KeyPair aheadDbNodeKeys = loadKeyPair(dbAhead);
    final SynchronizerConfiguration fastSyncConfig =
        SynchronizerConfiguration.builder()
            .syncMode(mode)
            // TODO: Disable switch from fast to full sync via configuration for now, set pivot to
            // realistic value when world state persistence is added.
            //        .fastSyncPivotDistance(blockCount / 2).build();
            .fastSyncPivotDistance(0)
            .build();

    // Setup state with block data
    try (final PantheonController<Void, EthHashBlockMiner> controller =
        MainnetPantheonController.init(
            dbAhead,
            GenesisConfig.mainnet(),
            fastSyncConfig,
            new MiningParametersTestBuilder().enabled(false).build(),
            NETWORK_ID,
            aheadDbNodeKeys)) {
      setupState(blockCount, controller.getProtocolSchedule(), controller.getProtocolContext());
    }

    // Setup Runner with blocks
    final PantheonController<Void, EthHashBlockMiner> controllerAhead =
        MainnetPantheonController.init(
            dbAhead,
            GenesisConfig.mainnet(),
            fastSyncConfig,
            new MiningParametersTestBuilder().enabled(false).build(),
            NETWORK_ID,
            aheadDbNodeKeys);
    final String listenHost = InetAddress.getLoopbackAddress().getHostAddress();
    final ExecutorService executorService = Executors.newFixedThreadPool(2);
    final JsonRpcConfiguration aheadJsonRpcConfiguration = jsonRpcConfiguration();
    final WebSocketConfiguration aheadWebSocketConfiguration = wsRpcConfiguration();
    final RunnerBuilder runnerBuilder = new RunnerBuilder();
    final Runner runnerAhead =
        runnerBuilder.build(
            Vertx.vertx(),
            controllerAhead,
            true,
            Collections.emptyList(),
            listenHost,
            0,
            3,
            aheadJsonRpcConfiguration,
            aheadWebSocketConfiguration,
            dbAhead);
    try {

      executorService.submit(runnerAhead::execute);
      final JsonRpcConfiguration behindJsonRpcConfiguration = jsonRpcConfiguration();
      final WebSocketConfiguration behindWebSocketConfiguration = wsRpcConfiguration();

      // Setup runner with no block data
      final Path dbBehind = temp.newFolder().toPath();
      final KeyPair behindDbNodeKeys = loadKeyPair(dbBehind);
      final PantheonController<Void, EthHashBlockMiner> controllerBehind =
          MainnetPantheonController.init(
              temp.newFolder().toPath(),
              GenesisConfig.mainnet(),
              fastSyncConfig,
              new MiningParametersTestBuilder().enabled(false).build(),
              NETWORK_ID,
              behindDbNodeKeys);
      final Runner runnerBehind =
          runnerBuilder.build(
              Vertx.vertx(),
              controllerBehind,
              true,
              Collections.singletonList(
                  new DefaultPeer(
                      aheadDbNodeKeys.getPublicKey().getEncodedBytes(),
                      listenHost,
                      runnerAhead.getP2pUdpPort(),
                      runnerAhead.getP2pTcpPort())),
              listenHost,
              0,
              3,
              behindJsonRpcConfiguration,
              behindWebSocketConfiguration,
              dbBehind);

      executorService.submit(runnerBehind::execute);
      final Call.Factory client = new OkHttpClient();
      Awaitility.await()
          .ignoreExceptions()
          .atMost(5L, TimeUnit.MINUTES)
          .untilAsserted(
              () -> {
                final String baseUrl =
                    String.format("http://%s:%s", listenHost, runnerBehind.getJsonRpcPort().get());
                try (final Response resp =
                    client
                        .newCall(
                            new Request.Builder()
                                .post(
                                    RequestBody.create(
                                        MediaType.parse("application/json; charset=utf-8"),
                                        "{\"jsonrpc\":\"2.0\",\"id\":"
                                            + Json.encode(7)
                                            + ",\"method\":\"eth_syncing\"}"))
                                .url(baseUrl)
                                .build())
                        .execute()) {

                  assertThat(resp.code()).isEqualTo(200);

                  final int currentBlock =
                      UInt256.fromHexString(
                              new JsonObject(resp.body().string())
                                  .getJsonObject("result")
                                  .getString("currentBlock"))
                          .toInt();
                  assertThat(currentBlock).isEqualTo(blockCount);
                }
              });

      final Future<Void> future = Future.future();
      final HttpClient httpClient = Vertx.vertx().createHttpClient();
      httpClient.websocket(
          runnerBehind.getWebsocketPort().get(),
          WebSocketConfiguration.DEFAULT_WEBSOCKET_HOST,
          "/",
          ws -> {
            ws.write(
                Buffer.buffer(
                    "{\"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"syncing\"]}"));
            ws.handler(
                buffer -> {
                  final boolean matches =
                      buffer.toString().equals("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":\"0x0\"}");
                  if (matches) {
                    future.complete();
                  } else {
                    future.fail("Unexpected result");
                  }
                });
          });
      Awaitility.await()
          .catchUncaughtExceptions()
          .atMost(5L, TimeUnit.MINUTES)
          .until(future::isComplete);
    } finally {
      executorService.shutdownNow();
      if (!executorService.awaitTermination(2L, TimeUnit.MINUTES)) {
        Assertions.fail("One of the two Pantheon runs failed to cleanly join.");
      }
    }
  }

  private JsonRpcConfiguration jsonRpcConfiguration() {
    final JsonRpcConfiguration configuration = JsonRpcConfiguration.createDefault();
    configuration.setPort(0);
    configuration.setEnabled(true);
    return configuration;
  }

  private WebSocketConfiguration wsRpcConfiguration() {
    final WebSocketConfiguration configuration = WebSocketConfiguration.createDefault();
    configuration.setPort(0);
    configuration.setEnabled(true);
    return configuration;
  }

  private static void setupState(
      final int count,
      final ProtocolSchedule<Void> protocolSchedule,
      final ProtocolContext<Void> protocolContext) {
    final List<Block> blocks = BlockSyncTestUtils.firstBlocks(count + 1);

    for (int i = 1; i < count + 1; ++i) {
      final Block block = blocks.get(i);
      final ProtocolSpec<Void> protocolSpec =
          protocolSchedule.getByBlockNumber(block.getHeader().getNumber());
      final BlockImporter<Void> blockImporter = protocolSpec.getBlockImporter();
      final boolean result =
          blockImporter.importBlock(protocolContext, block, HeaderValidationMode.FULL);
      if (!result) {
        throw new IllegalStateException("Unable to import block " + block.getHeader().getNumber());
      }
    }
  }
}
