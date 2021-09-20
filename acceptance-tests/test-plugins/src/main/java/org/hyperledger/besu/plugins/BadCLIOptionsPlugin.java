/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.plugins;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import com.google.auto.service.AutoService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Option;

@AutoService(BesuPlugin.class)
public class BadCLIOptionsPlugin implements BesuPlugin {
  private static final Logger LOG = LogManager.getLogger();

  @Option(names = "--poorly-named-option")
  String poorlyNamedOption = "nothing";

  private File callbackDir;

  @Override
  public void register(final BesuContext context) {
    LOG.info("Registering BadCliOptionsPlugin");
    callbackDir = new File(System.getProperty("besu.plugins.dir", "plugins"));
    writeStatus("init");

    writeStatus("register");
  }

  @Override
  public void start() {
    LOG.info("Starting BadCliOptionsPlugin");
    writeStatus("start");
  }

  @Override
  public void stop() {
    LOG.info("Stopping BadCliOptionsPlugin");
    writeStatus("stop");
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void writeStatus(final String status) {
    try {
      final File callbackFile = new File(callbackDir, "badCLIOptions." + status);
      if (!callbackFile.getParentFile().exists()) {
        callbackFile.getParentFile().mkdirs();
        callbackFile.getParentFile().deleteOnExit();
      }
      Files.write(callbackFile.toPath(), status.getBytes(UTF_8));
      callbackFile.deleteOnExit();
      LOG.info("Write status " + status);
    } catch (final IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  @Override
  public Optional<String> getName() {
    return Optional.of("bad-cli");
  }
}
