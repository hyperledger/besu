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
    boolean containsLowercase =
        metricCategoryConverter.getMetricCategories().keySet().stream()
            .anyMatch(testString -> testString.chars().anyMatch(Character::isLowerCase));
    assertThat(containsLowercase).isFalse();
  }
}
