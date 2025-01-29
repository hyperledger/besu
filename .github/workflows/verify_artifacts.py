import requests
import argparse


def create_artifact_paths(version):
    artifacts_base_path = "https://hyperledger.jfrog.io/hyperledger/besu-maven/org/hyperledger/besu"
    # add to this list here to update the list of artifacts to check 
    artifacts = [
        # besu/evm
        f"{artifacts_base_path}/evm/{version}/evm-{version}.module",
        f"{artifacts_base_path}/evm/{version}/evm-{version}.pom",
        f"{artifacts_base_path}/evm/{version}/evm-{version}.jar",
        # besu/plugin-api
        f"{artifacts_base_path}/plugin-api/{version}/plugin-api-{version}.module",
        f"{artifacts_base_path}/plugin-api/{version}/plugin-api-{version}.pom",
        f"{artifacts_base_path}/plugin-api/{version}/plugin-api-{version}.jar",
        # besu/metrics-core
        f"{artifacts_base_path}/internal/metrics-core/{version}/metrics-core-{version}.module",
        f"{artifacts_base_path}/internal/metrics-core/{version}/metrics-core-{version}.pom",
        f"{artifacts_base_path}/internal/metrics-core/{version}/metrics-core-{version}.jar",
        # besu/internal/core
        f"{artifacts_base_path}/internal/core/{version}/core-{version}.module",
        f"{artifacts_base_path}/internal/core/{version}/core-{version}.pom",
        f"{artifacts_base_path}/internal/core/{version}/core-{version}.jar",
        # besu/internal/config
        f"{artifacts_base_path}/internal/config/{version}/config-{version}.module",
        f"{artifacts_base_path}/internal/config/{version}/config-{version}.pom",
        f"{artifacts_base_path}/internal/config/{version}/config-{version}.jar",
        # bom
        f"{artifacts_base_path}/bom/{version}/bom-{version}.module",
        f"{artifacts_base_path}/bom/{version}/bom-{version}.pom",
    ]
    return artifacts



def check_url(url):
    print(f"Checking artifact at: {url}")
    r = requests.head(url)
    if (r.status_code != 200):
        raise Exception(f"Sorry, No artifact found at '{url}' !!!") 

def main():
    parser = argparse.ArgumentParser(description='Check besu artifacts')
    parser.add_argument('--besu_version', action="store", dest='besu_version', default="")
    args = parser.parse_args()
    print(args.besu_version)

    artifacts = create_artifact_paths(args.besu_version)
    print(artifacts)
    for url in artifacts:
        check_url(url)

if __name__ == "__main__":
    main()