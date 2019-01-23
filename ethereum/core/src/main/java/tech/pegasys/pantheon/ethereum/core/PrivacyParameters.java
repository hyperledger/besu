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

import java.io.File;
import java.net.URI;

public class PrivacyParameters {

  private static final String ORION_URL = "http://localhost:8888";
  public static final URI DEFAULT_ORION_URL = URI.create(ORION_URL);

  private boolean enabled;
  private String url;
  private File publicKey;

  public File getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(final File publicKey) {
    this.publicKey = publicKey;
  }

  public static PrivacyParameters noPrivacy() {
    final PrivacyParameters config = new PrivacyParameters();
    config.setEnabled(false);
    config.setUrl(ORION_URL);
    return config;
  }

  @Override
  public String toString() {
    return "PrivacyParameters{" + "enabled=" + enabled + ", url='" + url + '\'' + '}';
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public String getUrl() {
    return this.url;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }
}
