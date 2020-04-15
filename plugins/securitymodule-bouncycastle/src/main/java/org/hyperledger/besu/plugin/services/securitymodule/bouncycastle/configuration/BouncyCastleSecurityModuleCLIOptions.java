package org.hyperledger.besu.plugin.services.securitymodule.bouncycastle.configuration;

import picocli.CommandLine.Option;

import java.io.File;

public class BouncyCastleSecurityModuleCLIOptions {
    @Option(
            names = {"--node-private-key-file"},
            paramLabel = "<PATH>",
            description =
                    "The node's private key file (default: a file named \"key\" in the Besu data folder)")
    final File nodePrivateKeyFile = null;
}
