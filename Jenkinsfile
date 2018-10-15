#!/usr/bin/env groovy

if (env.BRANCH_NAME == "master") {
    properties([
        buildDiscarder(
            logRotator(
                daysToKeepStr: '90'
            )
        )
    ])
} else {
    properties([
        buildDiscarder(
            logRotator(
                numToKeepStr: '10'
            )
        )
    ])
}

stage('Pantheon tests') {
    parallel javaTests: {
        node {
            checkout scm
            docker.image('docker:18.06.0-ce-dind').withRun('--privileged') { d ->
                docker.image('pegasyseng/pantheon-build:0.0.1').inside("--link ${d.id}:docker") {
                    try {
                        stage('Compile') {
                            sh './gradlew --no-daemon --parallel clean compileJava'
                        }
                        stage('compile tests') {
                            sh './gradlew --no-daemon --parallel compileTestJava'
                        }
                        stage('assemble') {
                            sh './gradlew --no-daemon --parallel assemble'
                        }
                        stage('Build') {
                            sh './gradlew --no-daemon --parallel build'
                        }
                        stage('Reference tests') {
                            sh './gradlew --no-daemon --parallel referenceTest'
                        }
                        stage('Integration Tests') {
                            sh './gradlew --no-daemon --parallel integrationTest'
                        }
                        stage('Acceptance Tests') {
                            sh './gradlew --no-daemon --parallel acceptanceTest --tests Web3Sha3AcceptanceTest --tests PantheonClusterAcceptanceTest --tests MiningAcceptanceTest'
                        }
                        stage('Check Licenses') {
                            sh './gradlew --no-daemon --parallel checkLicenses'
                        }
                        stage('Check javadoc') {
                            sh './gradlew --no-daemon --parallel javadoc'
                        }
                        stage('Jacoco root report') {
                            sh './gradlew --no-daemon jacocoRootReport'
                        }
                    } finally {
                        archiveArtifacts '**/build/reports/**'
                        archiveArtifacts '**/build/test-results/**'
                        archiveArtifacts 'build/reports/**'
                        archiveArtifacts 'build/distributions/**'

                        junit '**/build/test-results/**/*.xml'
                    }
                }
            }
        }
    }, quickstartTests: {
        node {
            checkout scm
            docker.image('docker:18.06.0-ce-dind').withRun('--privileged') { d ->
                docker.image('pegasyseng/pantheon-build:0.0.1').inside("--link ${d.id}:docker") {
                    try {
                        stage('Docker quickstart Tests') {
                            sh 'DOCKER_HOST=tcp://docker:2375 ./gradlew --no-daemon --parallel clean dockerQuickstartTest'
                        }
                    } finally {
                        archiveArtifacts '**/build/test-results/**'
                        archiveArtifacts '**/build/reports/**'

                        junit '**/build/test-results/**/*.xml'
                    }
                }
            }
        }
    }
}
