package net.consensys.pantheon.tests;

import static com.google.common.io.MoreFiles.deleteDirectoryContents;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import com.google.common.io.RecursiveDeleteOption;
import io.vertx.core.json.Json;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PantheonSmokeTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path pantheonBin;

  @Before
  public void before() throws IOException {
    final String dist = System.getProperty("pantheon.test.distribution");
    if (dist == null) {
      throw new IllegalStateException(
          "System property 'pantheon.test.distribution' must be set in order for this test to work.");
    }
    final Path pantheonBase = Paths.get(dist);
    final Path dataDirectory = pantheonBase.resolve("data");
    Files.createDirectories(dataDirectory);
    deleteDirectoryContents(dataDirectory, RecursiveDeleteOption.ALLOW_INSECURE);
    pantheonBin = pantheonBase.resolve("bin").resolve("pantheon");
  }

  @Test
  public void startsWithoutArguments() throws Exception {
    final File stdout = temporaryFolder.newFile();
    final File stderr = temporaryFolder.newFile();
    final ProcessBuilder processBuilder =
        new ProcessBuilder(pantheonBin.toString()).redirectOutput(stdout).redirectError(stderr);
    processBuilder.environment().remove("JAVA_TOOL_OPTIONS");
    final Process pantheon = processBuilder.start();
    try {

      final OkHttpClient client = new OkHttpClient.Builder().build();
      waitForPort(JsonRpcConfiguration.DEFAULT_JSON_RPC_PORT);

      try (Response jsonRpcResponse =
          client
              .newCall(
                  new Request.Builder()
                      .url("http://localhost:" + JsonRpcConfiguration.DEFAULT_JSON_RPC_PORT)
                      .post(
                          RequestBody.create(
                              MediaType.parse("application/json; charset=utf-8"),
                              "{\"jsonrpc\":\"2.0\",\"id\":"
                                  + Json.encode(1)
                                  + ",\"method\":\"web3_clientVersion\"}"))
                      .build())
              .execute()) {
        assertThat(jsonRpcResponse.code()).isEqualTo(HttpURLConnection.HTTP_OK);
      }

      pantheon.destroy();
      assertThat(pantheon.waitFor(10L, TimeUnit.SECONDS)).isTrue();
      // When JVM receives SIGTERM it exits 143
      assertThat(pantheon.exitValue()).isEqualTo(143);

    } finally {
      pantheon.destroyForcibly();
      pantheon.waitFor();
    }
  }

  private static void waitForPort(final int port) {
    Awaitility.await()
        .ignoreExceptions()
        .until(
            () -> {
              try (Socket client = new Socket(InetAddress.getLoopbackAddress(), port)) {
                return true;
              }
            });
  }
}
