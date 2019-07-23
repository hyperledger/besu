/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.testutil;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.io.Resources;

public final class BlockTestUtil {

  private static final Supplier<URL> blockchainURLSupplier =
      Suppliers.memoize(BlockTestUtil::supplyTestBlockchainURL);
  private static final Supplier<URL> genesisURLSupplier =
      Suppliers.memoize(BlockTestUtil::supplyTestGenesisURL);

  private static URL supplyTestBlockchainURL() {
    return ensureFileUrl(BlockTestUtil.class.getClassLoader().getResource("testBlockchain.blocks"));
  }

  private static URL supplyTestGenesisURL() {
    return ensureFileUrl(BlockTestUtil.class.getClassLoader().getResource("testGenesis.json"));
  }

  public static URL getTestBlockchainUrl() {
    return blockchainURLSupplier.get();
  }

  public static URL getTestGenesisUrl() {
    return genesisURLSupplier.get();
  }

  /** Take a resource URL and if needed copy it to a temp file and return that URL. */
  private static URL ensureFileUrl(final URL resource) {
    Preconditions.checkNotNull(resource);
    try {
      try {
        Paths.get(resource.toURI());
      } catch (final FileSystemNotFoundException e) {
        final Path target = Files.createTempFile("pantheon", null);
        target.toFile().deleteOnExit();
        Files.copy(resource.openStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toUri().toURL();
      }
    } catch (final IOException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
    return resource;
  }

  /**
   * Writes the first 1000 blocks of the public chain to the given file.
   *
   * @param target FIle to write blocks to
   */
  public static void write1000Blocks(final Path target) {
    try {
      Files.write(
          target,
          Resources.toByteArray(BlockTestUtil.class.getResource("/1000.blocks")),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
