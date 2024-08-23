package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import com.fasterxml.jackson.annotation.JsonGetter;

public class EngineGetClientVersionResultV1 {
    private final String code;
    private final String name;
    private final String version;
    private final String commit;

    public EngineGetClientVersionResultV1(final String code, final String name, final String version, final String commit) {
        this.code = code;
        this.name = name;
        this.version = version;
        this.commit = commit;
    }

    @JsonGetter(value = "code")
    public String getCode() {
        return code;
    }

    @JsonGetter(value = "name")
    public String getName() {
        return name;
    }

    @JsonGetter(value = "version")
    public String getVersion() {
        return version;
    }

    @JsonGetter(value = "commit")
    public String getCommit() {
        return commit;
    }
}
