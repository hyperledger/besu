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
package org.hyperledger.besu.cli.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.plugin.services.metrics.MetricCategory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MetricCategoryConverterTest {

  private MetricCategoryConverter metricCategoryConverter;

  @Mock MetricCategory metricCategory;

  @Before
  public void setUp() {
    metricCategoryConverter = new MetricCategoryConverter();
  }

  @Test
  public void convertShouldFailIfValueNotRegistered() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> metricCategoryConverter.convert("notRegistered"));
  }

  @Test
  public void addRegistryCategoryShouldUppercaseInputValues() {
    when(metricCategory.getName()).thenReturn("testcat");
    metricCategoryConverter.addRegistryCategory(metricCategory);
    when(metricCategory.getName()).thenReturn("tesTCat2");
    metricCategoryConverter.addRegistryCategory(metricCategory);

    final boolean containsLowercase =
        metricCategoryConverter.getMetricCategories().keySet().stream()
            .anyMatch(testString -> testString.chars().anyMatch(Character::isLowerCase));

    assertThat(containsLowercase).isFalse();
    assertThat(metricCategoryConverter.getMetricCategories().size()).isEqualTo(2);
    assertThat(metricCategoryConverter.getMetricCategories().keySet())
        .containsExactlyInAnyOrder("TESTCAT", "TESTCAT2");
  }
}
