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
 */
package org.hyperledger.besu.testutil;

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

  private static final Supplier<ChainResources> testChainSupplier =
      Suppliers.memoize(BlockTestUtil::supplyTestChainResources);
  private static final Supplier<ChainResources> testChainLondonSupplier =
      Suppliers.memoize(BlockTestUtil::supplyTestChainLondonResources);
  private static final Supplier<ChainResources> mainnetChainSupplier =
      Suppliers.memoize(BlockTestUtil::supplyMainnetChainResources);
  private static final Supplier<ChainResources> badPowChainSupplier =
      Suppliers.memoize(BlockTestUtil::supplyBadPowChainResources);
  private static final Supplier<ChainResources> forkOutdatedSupplier =
      Suppliers.memoize(BlockTestUtil::supplyOutdatedForkResources);
  private static final Supplier<ChainResources> forkUpgradedSupplier =
      Suppliers.memoize(BlockTestUtil::supplyUpgradedForkResources);

  public static URL getTestBlockchainUrl() {
    return getTestChainResources().getBlocksURL();
  }

  public static URL getTestLondonBlockchainUrl() {
    return getTestChainLondonResources().getBlocksURL();
  }

  public static URL getTestGenesisUrl() {
    return getTestChainResources().getGenesisURL();
  }

  public static URL getTestLondonGenesisUrl() {
    return getTestChainLondonResources().getGenesisURL();
  }

  public static ChainResources getTestChainResources() {
    return testChainSupplier.get();
  }

  public static ChainResources getTestChainLondonResources() {
    return testChainLondonSupplier.get();
  }

  public static ChainResources getMainnetResources() {
    return mainnetChainSupplier.get();
  }

  private static ChainResources getBadPowResources() {
    return badPowChainSupplier.get();
  }

  public static ChainResources getOutdatedForkResources() {
    return forkOutdatedSupplier.get();
  }

  public static ChainResources getUpgradedForkResources() {
    return forkUpgradedSupplier.get();
  }

  private static ChainResources supplyTestChainResources() {
    final URL genesisURL =
        ensureFileUrl(BlockTestUtil.class.getClassLoader().getResource("testGenesis.json"));
    final URL blocksURL =
        ensureFileUrl(BlockTestUtil.class.getClassLoader().getResource("testBlockchain.blocks"));
    return new ChainResources(genesisURL, blocksURL);
  }

  private static ChainResources supplyTestChainLondonResources() {
    final URL genesisURL =
        ensureFileUrl(
            BlockTestUtil.class
                .getClassLoader()
                .getResource("fork-london-data/testLondonGenesis.json"));
    final URL blocksURL =
        ensureFileUrl(
            BlockTestUtil.class
                .getClassLoader()
                .getResource("fork-london-data/testLondonBlockchain.blocks"));
    return new ChainResources(genesisURL, blocksURL);
  }

  private static ChainResources supplyMainnetChainResources() {
    final URL genesisURL =
        ensureFileUrl(
            BlockTestUtil.class.getClassLoader().getResource("mainnet-data/mainnet.json"));
    final URL blocksURL =
        ensureFileUrl(BlockTestUtil.class.getClassLoader().getResource("mainnet-data/1000.blocks"));
    return new ChainResources(genesisURL, blocksURL);
  }

  private static ChainResources supplyBadPowChainResources() {
    final URL genesisURL =
        ensureFileUrl(
            BlockTestUtil.class.getClassLoader().getResource("mainnet-data/mainnet.json"));
    final URL blocksURL =
        ensureFileUrl(
            BlockTestUtil.class.getClassLoader().getResource("mainnet-data/badpow.blocks"));
    return new ChainResources(genesisURL, blocksURL);
  }

  private static ChainResources supplyOutdatedForkResources() {
    final URL genesisURL =
        ensureFileUrl(
            BlockTestUtil.class
                .getClassLoader()
                .getResource("fork-chain-data/genesis-outdated.json"));
    final URL blocksURL =
        ensureFileUrl(
            BlockTestUtil.class
                .getClassLoader()
                .getResource("fork-chain-data/fork-outdated.blocks"));
    return new ChainResources(genesisURL, blocksURL);
  }

  private static ChainResources supplyUpgradedForkResources() {
    final URL genesisURL =
        ensureFileUrl(
            BlockTestUtil.class
                .getClassLoader()
                .getResource("fork-chain-data/genesis-upgraded.json"));
    final URL blocksURL =
        ensureFileUrl(
            BlockTestUtil.class
                .getClassLoader()
                .getResource("fork-chain-data/fork-upgraded.blocks"));
    return new ChainResources(genesisURL, blocksURL);
  }

  /** Take a resource URL and if needed copy it to a temp file and return that URL. */
  private static URL ensureFileUrl(final URL resource) {
    Preconditions.checkNotNull(resource);
    try {
      try {
        Paths.get(resource.toURI());
      } catch (final FileSystemNotFoundException e) {
        final Path target = Files.createTempFile("besu", null);
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
          Resources.toByteArray(getMainnetResources().getBlocksURL()),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Writes the first 1000 blocks of the public chain to the given file.
   *
   * @param target FIle to write blocks to
   */
  public static void writeBadPowBlocks(final Path target) {
    try {
      Files.write(
          target,
          Resources.toByteArray(getBadPowResources().getBlocksURL()),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public static class ChainResources {
    private final URL genesisURL;
    private final URL blocksURL;

    public ChainResources(final URL genesisURL, final URL blocksURL) {
      this.genesisURL = genesisURL;
      this.blocksURL = blocksURL;
    }

    public URL getGenesisURL() {
      return genesisURL;
    }

    public URL getBlocksURL() {
      return blocksURL;
    }
  }
}
