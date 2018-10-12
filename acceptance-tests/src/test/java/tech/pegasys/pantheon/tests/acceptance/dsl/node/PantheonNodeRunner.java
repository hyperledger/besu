package tech.pegasys.pantheon.tests.acceptance.dsl.node;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.awaitility.Awaitility;

public interface PantheonNodeRunner {

  static PantheonNodeRunner instance() {
    if (Boolean.getBoolean("acctests.runPantheonAsProcess")) {
      return new ProcessPantheonNodeRunner();
    } else {
      return new ThreadPantheonNodeRunner();
    }
  }

  void startNode(PantheonNode node);

  void stopNode(PantheonNode node);

  void shutdown();

  default void waitForPortsFile(final Path dataDir) {
    final File file = new File(dataDir.toFile(), "pantheon.ports");
    Awaitility.waitAtMost(30, TimeUnit.SECONDS)
        .until(
            () -> {
              if (file.exists()) {
                try (Stream<String> s = Files.lines(file.toPath())) {
                  return s.count() > 0;
                }
              } else {
                return false;
              }
            });
  }
}
