package org.hyperledger.besu.plugin.services.storage.rocksdb.configuration;

public enum PrivateVersionedStorageFormat {
  ORIGINAL( 1);

  private final int privacyVersion;

  PrivateVersionedStorageFormat(
      final int privacyVersion) {
    this.privacyVersion = privacyVersion;
  }

  public static PrivateVersionedStorageFormat defaultForNewDB() {
    return ORIGINAL;
  }

  public int getPrivacyVersion() {
    return privacyVersion;
  }


  @Override
  public String toString() {
    return
            "privacyVersion=" + privacyVersion
           ;
  }
}
