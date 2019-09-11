/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.plugins;

import tech.pegasys.pantheon.plugin.PantheonContext;
import tech.pegasys.pantheon.plugin.PantheonPlugin;
import tech.pegasys.pantheon.plugin.data.BlockHeader;
import tech.pegasys.pantheon.plugin.services.PantheonEvents;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.auto.service.AutoService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@AutoService(PantheonPlugin.class)
public class TestPantheonEventsPlugin implements PantheonPlugin {
  private static final Logger LOG = LogManager.getLogger();

  private PantheonContext context;

  private Optional<Long> subscriptionId;
  private final AtomicInteger blockCounter = new AtomicInteger();
  private File callbackDir;

  @Override
  public void register(final PantheonContext context) {
    this.context = context;
    LOG.info("Registered");
    callbackDir = new File(System.getProperty("pantheon.plugins.dir", "plugins"));
  }

  @Override
  public void start() {
    subscriptionId =
        context
            .getService(PantheonEvents.class)
            .map(events -> events.addBlockPropagatedListener(this::onBlockAnnounce));
    LOG.info("Listening with ID#" + subscriptionId);
  }

  @Override
  public void stop() {
    subscriptionId.ifPresent(
        id ->
            context
                .getService(PantheonEvents.class)
                .ifPresent(pantheonEvents -> pantheonEvents.removeBlockPropagatedListener(id)));
    LOG.info("No longer listening with ID#" + subscriptionId);
  }

  private void onBlockAnnounce(final BlockHeader header) {
    final int blockCount = blockCounter.incrementAndGet();
    LOG.info("I got a new block! (I've seen {}) - {}", blockCount, header);
    try {
      final File callbackFile = new File(callbackDir, "newBlock." + blockCount);
      if (!callbackFile.getParentFile().exists()) {
        callbackFile.getParentFile().mkdirs();
        callbackFile.getParentFile().deleteOnExit();
      }
      Files.write(callbackFile.toPath(), Collections.singletonList(header.toString()));
      callbackFile.deleteOnExit();
    } catch (final IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }
}
