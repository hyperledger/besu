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
package org.hyperledger.besu.nat.kubernetes;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.nat.kubernetes.KubernetesNatManager.DEFAULT_BESU_SERVICE_NAME_FILTER;
import static org.mockito.Mockito.when;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class KubernetesUnknownNatManagerTest {

  @Mock private V1Service v1Service;

  private KubernetesNatManager natManager;

  @Before
  public void initialize() {

    when(v1Service.getSpec()).thenReturn(new V1ServiceSpec().type("Unknown"));
    when(v1Service.getMetadata())
        .thenReturn(new V1ObjectMeta().name(DEFAULT_BESU_SERVICE_NAME_FILTER));
    natManager = new KubernetesNatManager(DEFAULT_BESU_SERVICE_NAME_FILTER);
    try {
      natManager.start();
    } catch (Exception ignored) {
      System.err.println("Ignored missing Kube config file in testing context.");
    }
  }

  @Test
  public void assertThatNatExceptionIsThrownWithUnknownServiceType() {
    assertThatThrownBy(() -> natManager.updateUsingBesuService(v1Service))
        .isInstanceOf(RuntimeException.class);
  }
}
