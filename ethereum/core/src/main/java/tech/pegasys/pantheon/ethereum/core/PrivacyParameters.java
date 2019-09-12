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
package tech.pegasys.pantheon.ethereum.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.privacy.PrivateStateStorage;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionStorage;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStatePreimageStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import com.google.common.io.Files;

public class PrivacyParameters {

  public static final URI DEFAULT_ENCLAVE_URL = URI.create("http://localhost:8888");
  public static final PrivacyParameters DEFAULT = new PrivacyParameters();

  private Integer privacyAddress = Address.PRIVACY;
  private boolean enabled;
  private URI enclaveUri;
  private String enclavePublicKey;
  private File enclavePublicKeyFile;
  private Optional<SECP256K1.KeyPair> signingKeyPair = Optional.empty();
  private WorldStateArchive privateWorldStateArchive;
  private StorageProvider privateStorageProvider;

  private PrivateTransactionStorage privateTransactionStorage;
  private PrivateStateStorage privateStateStorage;

  public Integer getPrivacyAddress() {
    return privacyAddress;
  }

  public void setPrivacyAddress(final Integer privacyAddress) {
    this.privacyAddress = privacyAddress;
  }

  public Boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public URI getEnclaveUri() {
    return enclaveUri;
  }

  public void setEnclaveUri(final URI enclaveUri) {
    this.enclaveUri = enclaveUri;
  }

  public String getEnclavePublicKey() {
    return enclavePublicKey;
  }

  public void setEnclavePublicKey(final String enclavePublicKey) {
    this.enclavePublicKey = enclavePublicKey;
  }

  public File getEnclavePublicKeyFile() {
    return enclavePublicKeyFile;
  }

  public void setEnclavePublicKeyFile(final File enclavePublicKeyFile) {
    this.enclavePublicKeyFile = enclavePublicKeyFile;
  }

  public Optional<SECP256K1.KeyPair> getSigningKeyPair() {
    return signingKeyPair;
  }

  public void setSigningKeyPair(final SECP256K1.KeyPair signingKeyPair) {
    this.signingKeyPair = Optional.ofNullable(signingKeyPair);
  }

  public WorldStateArchive getPrivateWorldStateArchive() {
    return privateWorldStateArchive;
  }

  public void setPrivateWorldStateArchive(final WorldStateArchive privateWorldStateArchive) {
    this.privateWorldStateArchive = privateWorldStateArchive;
  }

  public StorageProvider getPrivateStorageProvider() {
    return privateStorageProvider;
  }

  public void setPrivateStorageProvider(final StorageProvider privateStorageProvider) {
    this.privateStorageProvider = privateStorageProvider;
  }

  public PrivateTransactionStorage getPrivateTransactionStorage() {
    return privateTransactionStorage;
  }

  public void setPrivateTransactionStorage(
      final PrivateTransactionStorage privateTransactionStorage) {
    this.privateTransactionStorage = privateTransactionStorage;
  }

  public PrivateStateStorage getPrivateStateStorage() {
    return privateStateStorage;
  }

  public void setPrivateStateStorage(final PrivateStateStorage privateStateStorage) {
    this.privateStateStorage = privateStateStorage;
  }

  @Override
  public String toString() {
    return "PrivacyParameters{" + "enabled=" + enabled + ", enclaveUri='" + enclaveUri + '\'' + '}';
  }

  public static class Builder {

    private boolean enabled;
    private URI enclaveUrl;
    private Integer privacyAddress = Address.PRIVACY;
    private File enclavePublicKeyFile;
    private String enclavePublicKey;
    private Path privateKeyPath;
    private StorageProvider storageProvider;

    public Builder setPrivacyAddress(final Integer privacyAddress) {
      this.privacyAddress = privacyAddress;
      return this;
    }

    public Builder setEnclaveUrl(final URI enclaveUrl) {
      this.enclaveUrl = enclaveUrl;
      return this;
    }

    public Builder setEnabled(final boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder setStorageProvider(final StorageProvider privateStorageProvider) {
      this.storageProvider = privateStorageProvider;
      return this;
    }

    public Builder setPrivateKeyPath(final Path privateKeyPath) {
      this.privateKeyPath = privateKeyPath;
      return this;
    }

    public PrivacyParameters build() throws IOException {
      final PrivacyParameters config = new PrivacyParameters();
      if (enabled) {
        final WorldStateStorage privateWorldStateStorage =
            storageProvider.createWorldStateStorage();
        final WorldStatePreimageStorage privatePreimageStorage =
            storageProvider.createWorldStatePreimageStorage();
        final WorldStateArchive privateWorldStateArchive =
            new WorldStateArchive(privateWorldStateStorage, privatePreimageStorage);

        final PrivateTransactionStorage privateTransactionStorage =
            storageProvider.createPrivateTransactionStorage();
        final PrivateStateStorage privateStateStorage = storageProvider.createPrivateStateStorage();

        config.setPrivateWorldStateArchive(privateWorldStateArchive);
        config.setEnclavePublicKey(enclavePublicKey);
        config.setEnclavePublicKeyFile(enclavePublicKeyFile);
        config.setPrivateStorageProvider(storageProvider);
        config.setPrivateTransactionStorage(privateTransactionStorage);
        config.setPrivateStateStorage(privateStateStorage);
        if (privateKeyPath != null) {
          config.setSigningKeyPair(KeyPair.load(privateKeyPath.toFile()));
        }
      }
      config.setEnabled(enabled);
      config.setEnclaveUri(enclaveUrl);
      config.setPrivacyAddress(privacyAddress);
      return config;
    }

    public Builder setEnclavePublicKeyUsingFile(final File publicKeyFile) throws IOException {
      this.enclavePublicKeyFile = publicKeyFile;
      this.enclavePublicKey = Files.asCharSource(publicKeyFile, UTF_8).read();
      return this;
    }
  }
}
