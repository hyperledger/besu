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

node {
    checkout scm
    docker.image('docker:18.06.0-ce-dind').withRun('--privileged') { d ->
        docker.image('openjdk:8u181-jdk').inside("--link ${d.id}:docker") {
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
                    sh './gradlew --no-daemon --parallel acceptanceTest'
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
