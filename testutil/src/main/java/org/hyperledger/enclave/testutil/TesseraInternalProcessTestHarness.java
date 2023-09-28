package org.hyperledger.enclave.testutil;

import static com.google.common.io.Files.readLines;
import static io.netty.util.internal.ObjectUtil.checkNonEmpty;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Charsets;
import org.assertj.core.util.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

public class TesseraInternalProcessTestHarness implements EnclaveTestHarness {
    private static final Logger LOG =
            LoggerFactory.getLogger(TesseraInternalProcessTestHarness.class);

    private final EnclaveConfiguration enclaveConfiguration;

    private final AtomicReference<Process> tesseraProcess = new AtomicReference<>();
    private File tempFolder;

    private final Map<String, Process> tesseraProcesses = new HashMap<>();

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private URI q2TUri;
    private URI nodeURI;

    protected TesseraInternalProcessTestHarness(final EnclaveConfiguration enclaveConfiguration) {
        this.enclaveConfiguration = enclaveConfiguration;
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    @Override
    public void start() {
        this.tempFolder = Files.newTemporaryFolder();
        this.tempFolder.deleteOnExit();
        LOG.info("Temporary directory: " + tempFolder.getAbsolutePath());
        try {
            final String configFile = createConfigFile();
            final Optional<String> enclaveStartScript = findTesseraStartScript();
            if (enclaveStartScript.isPresent()) {
                final List<String> commandArgs = createCommandArgs(configFile, enclaveStartScript.get());
                final List<String> jvmArgs = createJvmArgs();
                LOG.info("Starting: {}", String.join(" ", commandArgs));
                LOG.info("JVM Args: {}", String.join(" ", jvmArgs));
                startTessera(commandArgs, jvmArgs);
            } else {
                throw new Exception("Tessera dist not found");
            }

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void startTessera(final List<String> args, final List<String> jvmArgs) throws Exception {

        final ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.environment().put("JAVA_OPTS", String.join(" ", jvmArgs));

        final String path =
                String.format("build/acceptanceTestLogs/%s-tessera-output.txt", System.currentTimeMillis());
        processBuilder.redirectOutput(new File(path));

        try {
            final Process process = processBuilder.redirectErrorStream(true).start();
            tesseraProcess.set(process);
            tesseraProcesses.put(enclaveConfiguration.getName(), process);
            redirectTesseraOutput();
        } catch (final NullPointerException ex) {
            ex.printStackTrace();
            throw new NullPointerException("Check that application.jar property has been set");
        }

        final Optional<Path> uris = this.waitForTesseraUris();
        if (uris.isPresent()) {
            readTesseraUriFile(uris.get());
        } else {
            throw new TimeoutException("Tessera did not start");
        }
    }

    private void redirectTesseraOutput() {
        final Logger LOG = LoggerFactory.getLogger(Process.class);
        executorService.submit(
                () -> {
                    try (final BufferedReader reader =
                                 Stream.of(tesseraProcess.get().getInputStream())
                                         .map(InputStreamReader::new)
                                         .map(BufferedReader::new)
                                         .findAny()
                                         .get()) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            LOG.info(line);
                        }
                    } catch (final IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
    }

    private Optional<Path> waitForTesseraUris() throws InterruptedException {
        final CountDownLatch startUpLatch = new CountDownLatch(1);
        final Path tesseraUris = this.getTesseraUrisFileName();
        executorService.submit(
                () -> {
                    while (true) {
                        final boolean exists =
                                java.nio.file.Files.exists(tesseraUris, LinkOption.NOFOLLOW_LINKS);
                        if (exists) {
                            startUpLatch.countDown();
                            return;
                        }
                        try {
                            LOG.info("Waiting for Tessera...");
                            TimeUnit.MILLISECONDS.sleep(3000);
                        } catch (final InterruptedException ex) {
                            LOG.error(ex.getMessage());
                        }
                    }
                });
        return startUpLatch.await(30, TimeUnit.SECONDS) ? Optional.of(tesseraUris) : Optional.empty();
    }

    private void readTesseraUriFile(final Path tesseraUris) {
        try {
            try (final Reader reader =
                         java.nio.file.Files.newBufferedReader(tesseraUris, StandardCharsets.UTF_8)) {
                final Properties properties = new Properties();
                properties.load(reader);

                final String q2tUri = properties.getProperty("Q2T");
                this.q2TUri = createUri(q2tUri, "Q2T");

                final String thirdPartyUri = properties.getProperty("THIRD_PARTY");
                createUri(thirdPartyUri, "THIRD_PARTY");

                final String nodeURI = properties.getProperty("P2P");
                this.nodeURI = createUri(nodeURI, "P2P");
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path getTesseraUrisFileName() {
        final String TESSERA_URI_FILE_NAME = "tessera.uris";
        return Paths.get(tempFolder.getAbsolutePath(), TESSERA_URI_FILE_NAME);
    }

    private URI createUri(final String url, final String type) {
        LOG.info("{} URI: {}", type, url);
        return URI.create(checkNonEmpty(url, type));
    }

    @Override
    public void stop() {
        if (tesseraProcess.get().isAlive()) {
            final Process p = tesseraProcess.get();
            p.descendants().forEach(ProcessHandle::destroy);
            p.destroy();
            try {
                FileUtils.forceDelete(tempFolder);
            } catch (final IOException e) {
                LOG.info("Temporary directory not deleted");
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public List<Path> getPublicKeyPaths() {
        return Arrays.asList(enclaveConfiguration.getPublicKeys());
    }

    @Override
    public String getDefaultPublicKey() {
        return readFile(enclaveConfiguration.getPublicKeys()[0]);
    }

    @Override
    public List<String> getPublicKeys() {
        return Arrays.stream(enclaveConfiguration.getPublicKeys())
                .map(TesseraInternalProcessTestHarness::readFile)
                .collect(Collectors.toList());
    }

    @Override
    public URI clientUrl() {
        return q2TUri;
    }

    @Override
    public URI nodeUrl() {
        return nodeURI;
    }

    @Override
    public void addOtherNode(final URI otherNode) {
        enclaveConfiguration.addOtherNode(otherNode.toString());
    }

    @Override
    public EnclaveType getEnclaveType() {
        return null;
    }

    private String createConfigFile() {
        String confString =
                "{\n"
                        + "    \"mode\": \"orion\","
                        + "    \"encryptor\":{\n"
                        + "        \"type\":\"NACL\",\n"
                        + "        \"properties\":{\n"
                        + "\n"
                        + "        }\n"
                        + "    },\n"
                        + "    \"useWhiteList\": false,\n"
                        + "    \"jdbc\": {\n"
                        + "        \"username\": \"sa\",\n"
                        + "        \"password\": \"\",\n"
                        + "        \"url\": \"jdbc:h2:"
                        + Path.of(tempFolder.getAbsolutePath(), "db")
                        + ";MODE=Oracle;TRACE_LEVEL_SYSTEM_OUT=0\",\n"
                        + "        \"autoCreateTables\": true\n"
                        + "    },\n"
                        + "    \"serverConfigs\":[\n"
                        + "        {\n"
                        + "            \"app\":\"ThirdParty\",\n"
                        + "            \"enabled\": true,\n"
                        + "            \"serverAddress\":\"http://127.0.0.1:0\",\n"
                        + "            \"cors\" : {\n"
                        + "                \"allowedMethods\" : [\"GET\", \"OPTIONS\"],\n"
                        + "                \"allowedOrigins\" : [\"*\"]\n"
                        + "            },\n"
                        + "            \"communicationType\" : \"REST\"\n"
                        + "        },\n"
                        + "        {\n"
                        + "            \"app\":\"Q2T\",\n"
                        + "            \"enabled\": true,\n"
                        + "            \"serverAddress\":\"http://localhost:0\",\n"
                        + "            \"communicationType\" : \"REST\"\n"
                        + "        },\n"
                        + "        {\n"
                        + "            \"app\":\"P2P\",\n"
                        + "            \"enabled\": true,\n"
                        + "            \"serverAddress\":\"http://127.0.0.1:0\",\n"
                        + "            \"communicationType\" : \"REST\"\n"
                        + "        }\n"
                        + "    ],\n"
                        + "    \"keys\": {\n"
                        + "        \"passwords\": [],\n"
                        + "        \"keyData\": [\n"
                        + "            {\n"
                        + "                \"privateKeyPath\": \""
                        + enclaveConfiguration.getPrivateKeys()[0].toString()
                        + "\",\n"
                        + "                \"publicKeyPath\": \""
                        + enclaveConfiguration.getPublicKeys()[0].toString()
                        + "\"\n"
                        + "            }\n"
                        + "        ]\n"
                        + "    },\n"
                        + "    \"alwaysSendTo\": []";

        if (enclaveConfiguration.getOtherNodes().size() != 0) {
            confString +=
                    ",\n"
                            + "    \"peer\": [\n"
                            + "        {\n"
                            + "            \"url\": \""
                            + enclaveConfiguration.getOtherNodes().get(0)
                            + "\"\n"
                            + "        }\n"
                            + "    ]";
        } else {
            confString += ",\n" + "    \"peer\": []";
        }

        confString += "\n}";

        LOG.info("Tessera config: \n" + confString);

        final File configFile =
                Files.newFile(Path.of(tempFolder.getAbsolutePath(), "config").toString());
        LOG.info("config file: " + configFile.getAbsolutePath());
        try {
            final FileWriter fw = new FileWriter(configFile, StandardCharsets.UTF_8);
            fw.write(confString);
            fw.close();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return configFile.getAbsolutePath();
    }

    private Optional<String> findTesseraStartScript() {
        final String path = System.getProperty("tessera-dist");
        return Optional.ofNullable(path);
    }

    private List<String> createCommandArgs(final String pathToConfigFile, final String startScript) {
        final List<String> command = new ArrayList<>();
        command.add(startScript);
        command.add("-configfile");
        command.add(pathToConfigFile);
        command.add("--debug");
        command.add("--XoutputServerURIPath");
        command.add(tempFolder.getAbsolutePath());
        return command;
    }

    private List<String> createJvmArgs() {
        final List<String> command = new ArrayList<>();
        command.add("-Xms128M");
        command.add("-Xmx128M");
        return command;
    }

    private static String readFile(final Path path) {
        try {
            return readLines(path.toFile(), Charsets.UTF_8).get(0);
        } catch (final IOException e) {
            LOG.error(e.getMessage());
            return "";
        }
    }
}
