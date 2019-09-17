#!/usr/bin/env node
"use strict";

const { execFileSync } = require('child_process');
const fs = require('fs-extra');
const toml = require('toml-j0.4')
const tomlEncoder = require('tomlify-j0.4')

if (process.argv.length < 4) {
    console.log("Missing argument");
    process.exit(1);
}

const chainId = process.argv[2];
if (chainId>99) {
    console.log("Use a chainId < 99");
    process.exit(2);
}

const nodeCount = process.argv[3];
if (nodeCount>=10) {
    console.log("Use a nodeCount < 10");
    process.exit(3);
}

const resourcesPath = `crosschain/resources`;
const destPath = process.env.HOME+`/crosschain_data`;
const basePort = 8000;
const chainPath = `${destPath}/chain${chainId}`;

var extradatas = [];

var i;
for (i = 0; i < nodeCount; i++) {
    const nodeNum = i.toString();
    const nodePath = `${chainPath}/node${nodeNum}`;

    if (i == 0) {
        fs.emptyDirSync(chainPath)
    }
    fs.emptyDirSync(nodePath)
    //fs.copySync(resourcesPath + "/config_template.toml", nodePath+"/config.toml")
    let config = toml.parse(fs.readFileSync(resourcesPath + "/config_template.toml").toString());
    let port = basePort + chainId*10 + nodeNum*1
    config['rpc-http-port'] = port
    config['miner-extra-data'] = "0x000000000000000000000000000000000000000000000000000000000000"+ chainId.padStart(2, '0') + nodeNum.padStart(2,'0');
    fs.writeFileSync(`${nodePath}/config.toml`,tomlEncoder.toToml(config,{ replace:
        function (key, value) {
            //let context = this;
            //let path = tomlEncoder.toKey(context.path);
            if (Number.isInteger(value)){
                return value.toFixed(0)
            }
            //if (/^more\.version\.\[\d+\]$/.test(path)) {
            //    return value.toFixed(0);  // Change the text transformed from the value.
            //}
            return false
        }
    }))

    console.log('Pantheon might show "illegal access" warnings because of JDK 12. Please ignore them.\n')

    let child = execFileSync('build/install/besu/bin/besu', [`--data-path=${nodePath}`,'public-key', 'export-address',`--to=${nodePath}/node-acct`]);

    let node_acct = fs.readFileSync(`${nodePath}/node-acct`).toString().trim();
    let node_acct_json = `["${node_acct}"]`;
    fs.writeFileSync(`${nodePath}/toEncode.json`, node_acct_json);

    child = execFileSync('build/install/besu/bin/besu', ['rlp','encode', `--from=${nodePath}/toEncode.json`,`--to=${nodePath}/extradata`], { stdio: 'ignore' });
    let extradata = fs.readFileSync(`${nodePath}/extradata`).toString().trim();
    extradatas.push(extradata)

    fs.unlinkSync(`${nodePath}/toEncode.json`);
    fs.unlinkSync(`${nodePath}/extradata`);
    //fs.unlinkSync(`${nodePath}/node-acct`);

    // Pantheon might show warnings, so make it clear that we finished successfully.
    console.log(`\nSUCCESS: created config files for chainId ${chainId} with node ${nodeNum} as a validator (port ${port}).`);
}

let genesis = JSON.parse(fs.readFileSync(`${resourcesPath}/genesis_template.json`).toString().trim());
genesis["extraData"] = extradatas;

genesis["config"]["chainId"] = parseInt(chainId);
fs.writeFileSync(`${chainPath}/genesis.json`, JSON.stringify(genesis, null, 4));