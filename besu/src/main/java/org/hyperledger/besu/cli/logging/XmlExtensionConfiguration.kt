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
package org.hyperledger.besu.cli.logging

import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.config.AbstractConfiguration
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.xml.XmlConfiguration
import org.apache.logging.log4j.core.layout.PatternLayout
import org.hyperledger.besu.cli.BesuCommand
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.stream.Stream

/** The Xml extension configuration for Logging framework.  */
class XmlExtensionConfiguration
/**
 * Instantiates a new Xml extension configuration.
 *
 * @param loggerContext the logger context
 * @param configSource the Configuration Source
 */
    (loggerContext: LoggerContext?, configSource: ConfigurationSource) :
    XmlConfiguration(loggerContext, configSource) {
    override fun doConfigure() {
        super.doConfigure()

        createConsoleAppender()
    }

    override fun reconfigure(): Configuration? {
        val refreshedParent = super.reconfigure()

        if (refreshedParent != null
            && AbstractConfiguration::class.java.isAssignableFrom(refreshedParent.javaClass)
        ) {
            try {
                val refreshed =
                    XmlExtensionConfiguration(
                        refreshedParent.loggerContext,
                        refreshedParent.configurationSource.resetInputStream()
                    )
                createConsoleAppender()
                return refreshed
            } catch (e: IOException) {
                LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
                    .error("Failed to reload the Log4j2 Xml configuration file", e)
            }
        }

        LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
            .warn("Cannot programmatically reconfigure loggers")
        return refreshedParent
    }

    private fun dim(string: String): String {
        return String.format("%%style{%s}{DIM}", string)
    }

    private fun colorize(string: String): String {
        return String.format("%%highlight{%s}{TRACE=normal}", string)
    }

    private val SEP = dim(" | ")

    private fun createConsoleAppender() {
        if (customLog4jConfigFilePresent()) {
            return
        }

        val patternLayout =
            PatternLayout.newBuilder()
                .withConfiguration(this)
                .withDisableAnsi(!BesuCommand.colorEnabled!!.orElse(!noColorSet()))
                .withNoConsoleNoAnsi(!BesuCommand.colorEnabled.orElse(false))
                .withPattern(
                    java.lang.String.join(
                        SEP,
                        dim("%d{yyyy-MM-dd HH:mm:ss.SSSZZZ}"),
                        dim("%t"),
                        colorize("%-5level"),
                        dim("%c{1}"),
                        colorize("%msgc%n%throwable")
                    )
                )
                .build()
        val consoleAppender =
            ConsoleAppender.newBuilder().setName("Console").setLayout(patternLayout).build()
        consoleAppender.start()
        this.rootLogger.addAppender(consoleAppender, null, null)
    }

    private fun customLog4jConfigFilePresent(): Boolean {
        return Stream.of("LOG4J_CONFIGURATION_FILE", "log4j.configurationFile")
            .flatMap { configFileKey: String? ->
                Stream.of(
                    System.getenv(configFileKey),
                    System.getProperty(configFileKey)
                )
            }
            .flatMap { t: String? -> Stream.ofNullable(t) }
            .findFirst()
            .isPresent
    }

    companion object {
        private fun noColorSet(): Boolean {
            return System.getenv("NO_COLOR") != null
        }
    }
}
