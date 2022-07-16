package org.hyperledger.besu.plugin.services.storage.leveldb;

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.StorageService;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.pegasys.leveldbjni.LevelDbJniLoader;

public class LevelDbPlugin implements BesuPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(LevelDbPlugin.class);
  private BesuContext context;

  private LevelDbKeyValueStorageFactory factory;

  public static boolean isLevelDbSupported() {
    // Use JNI to load as the native library is loaded in a static block
    try {
      LevelDbJniLoader.loadNativeLibrary();
      return true;
    } catch (final UnsatisfiedLinkError e) {
      LOG.info("LevelDB not supported on this system: {}", e.getMessage());
      return false;
    } catch (final Throwable e) {
      LOG.error("Failed to check LevelDB support. Defaulting to RocksDB.", e);
      return false;
    }
  }

  @Override
  public void register(final BesuContext context) {
    if (!isLevelDbSupported()) {
      LOG.debug("Not registering LevelDB plugin as LevelDB is not supported");
    }
    LOG.debug("Registering plugin");
    this.context = context;
    createFactoriesAndRegisterWithStorageService();

    LOG.debug("Plugin registered.");
  }

  @Override
  public void start() {
    LOG.debug("Starting plugin.");
    if (factory == null) {
      createFactoriesAndRegisterWithStorageService();
    }
  }

  @Override
  public void stop() {
    LOG.debug("Stopping plugin.");

    try {
      if (factory != null) {
        factory.close();
        factory = null;
      }
    } catch (final IOException e) {
      LOG.error("Failed to stop plugin: {}", e.getMessage(), e);
    }
  }

  private void createAndRegister(final StorageService service) {
    final List<SegmentIdentifier> segments = service.getAllSegmentIdentifiers();

    factory = new LevelDbKeyValueStorageFactory(segments);

    service.registerKeyValueStorage(factory);
  }

  private void createFactoriesAndRegisterWithStorageService() {
    context
        .getService(StorageService.class)
        .ifPresentOrElse(
            this::createAndRegister,
            () -> LOG.error("Failed to register KeyValueFactory due to missing StorageService."));
  }
}
