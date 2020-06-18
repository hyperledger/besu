package org.hyperledger.besu.cli.logging;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;

public class BesuLoggingConfigurationFactory extends ConfigurationFactory {

  @Override
  protected String[] getSupportedTypes() {
    return new String[] {".xml", "*"};
  }

  @Override
  public Configuration getConfiguration(
      final LoggerContext loggerContext, final ConfigurationSource source) {
    return new XmlExtensionConfiguration(loggerContext, source);
  }
}
