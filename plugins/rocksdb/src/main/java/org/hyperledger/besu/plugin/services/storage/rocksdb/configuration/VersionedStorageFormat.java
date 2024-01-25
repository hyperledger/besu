package org.hyperledger.besu.plugin.services.storage.rocksdb.configuration;

import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

public enum VersionedStorageFormat {
  FOREST_ORIGINAL(DataStorageFormat.FOREST, 1, 1),
  FOREST_WITH_VARIABLES(DataStorageFormat.FOREST, 2, 1),
  BONSAI_ORIGINAL(DataStorageFormat.BONSAI, 1, 1),
  BONSAI_WITH_VARIABLES(DataStorageFormat.BONSAI, 2, 1);

//  private static final Logger LOG = LoggerFactory.getLogger(VersionedStorageFormat.class);
  private final DataStorageFormat format;
  private final int version;
  private final int privacyVersion;

  VersionedStorageFormat(
      final DataStorageFormat format, final int version, final int privacyVersion) {
    this.format = format;
    this.version = version;
    this.privacyVersion = privacyVersion;
  }

  public static VersionedStorageFormat defaultForNewDB(final DataStorageFormat format) {
    return switch (format) {
      case FOREST -> FOREST_WITH_VARIABLES;
      case BONSAI -> BONSAI_WITH_VARIABLES;
    };
  }

  public DataStorageFormat getFormat() {
    return format;
  }

  public int getVersion() {
    return version;
  }

  public int getPrivacyVersion() {
    return privacyVersion;
  }

//  static VersionedStorageFormat fromMetadata(final DatabaseMetadata metadata) {
//    return Arrays.stream(values())
//        .filter(
//            vsf ->
//                vsf.format.equals(metadata.getFormat())
//                    && vsf.version == metadata.getVersion()
//                    && (metadata.maybePrivacyVersion().isPresent()
//                        ? metadata.maybePrivacyVersion().getAsInt() == vsf.privacyVersion
//                        : true))
//        .findFirst()
//        .orElseThrow(
//            () -> {
//              final String message = "Unsupported RocksDB metadata: " + metadata;
//              LOG.error(message);
//              throw new StorageException(message);
//            });
//  }


  @Override
  public String toString() {
    return
            "format=" + format +
            ", version=" + version +
            ", privacyVersion=" + privacyVersion
           ;
  }
}
