import requests
import argparse

artifacts_base_path = "https://hyperledger.jfrog.io/hyperledger/besu-maven/org/hyperledger/besu"
artifacts = [
    # besu/evm
    f"{artifacts_base_path}/evm/VERSION/evm-VERSION.module",
    f"{artifacts_base_path}/evm/VERSION/evm-VERSION.pom",
    f"{artifacts_base_path}/evm/VERSION/evm-VERSION.jar",
    # besu/plugin-api
    f"{artifacts_base_path}/plugin-api/VERSION/plugin-api-VERSION.module",
    f"{artifacts_base_path}/plugin-api/VERSION/plugin-api-VERSION.pom",
    f"{artifacts_base_path}/plugin-api/VERSION/plugin-api-VERSION.jar",
    # besu/metrics-core
    f"{artifacts_base_path}/internal/metrics-core/VERSION/metrics-core-VERSION.module",
    f"{artifacts_base_path}/internal/metrics-core/VERSION/metrics-core-VERSION.pom",
    f"{artifacts_base_path}/internal/metrics-core/VERSION/metrics-core-VERSION.jar",
    # besu/internal/core
    f"{artifacts_base_path}/internal/core/VERSION/core-VERSION.module",
    f"{artifacts_base_path}/internal/core/VERSION/core-VERSION.pom",
    f"{artifacts_base_path}/internal/core/VERSION/core-VERSION.jar",
    # besu/internal/config
    f"{artifacts_base_path}/internal/config/VERSION/config-VERSION.module",
    f"{artifacts_base_path}/internal/config/VERSION/config-VERSION.pom",
    f"{artifacts_base_path}/internal/config/VERSION/config-VERSION.jar"
]

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

    for url in artifacts:
        check_url(url.replace('VERSION', args.besu_version))


if __name__ == "__main__":
    main()