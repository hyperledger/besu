#!/usr/bin/env groovy

import hudson.model.Result
import hudson.model.Run
import jenkins.model.CauseOfInterruption.UserInterruption

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

def docker_image_dind = 'docker:18.06.0-ce-dind'
def docker_image = 'docker:18.06.0-ce'
def build_image = 'pegasyseng/pantheon-build:0.0.5-jdk11'

def abortPreviousBuilds() {
    Run previousBuild = currentBuild.rawBuild.getPreviousBuildInProgress()

    while (previousBuild != null) {
        if (previousBuild.isInProgress()) {
            def executor = previousBuild.getExecutor()
            if (executor != null) {
                echo ">> Aborting older build #${previousBuild.number}"
                executor.interrupt(Result.ABORTED, new UserInterruption(
                    "Aborted by newer build #${currentBuild.number}"
                ))
            }
        }

        previousBuild = previousBuild.getPreviousBuildInProgress()
    }
}

if (env.BRANCH_NAME != "master") {
    abortPreviousBuilds()
}

try {
    timeout(time: 1, unit: 'HOURS') {
        parallel UnitTests: {
            def stage_name = "Unit tests node: "
            node {
                checkout scm
                docker.image(docker_image_dind).withRun('--privileged') { d ->
                    docker.image(build_image).inside("--link ${d.id}:docker") {
                        try {
                            stage(stage_name + 'Prepare') {
                                sh './gradlew --no-daemon --parallel clean compileJava compileTestJava assemble'
                            }
                            stage(stage_name + 'Unit tests') {
                                sh './gradlew --no-daemon --parallel build'
                            }
                        } finally {
                            archiveArtifacts '**/build/reports/**'
                            archiveArtifacts '**/build/test-results/**'
                            archiveArtifacts 'build/reports/**'
                            archiveArtifacts 'build/distributions/**'

                            stash allowEmpty: true, includes: 'build/distributions/pantheon-*.tar.gz', name: 'distTarBall'

                            junit '**/build/test-results/**/*.xml'
                        }
                    }
                }
            }
        }, ReferenceTests: {
            def stage_name = "Reference tests node: "
            node {
                checkout scm
                docker.image(docker_image_dind).withRun('--privileged') { d ->
                    docker.image(build_image).inside("--link ${d.id}:docker") {
                        try {
                            stage(stage_name + 'Prepare') {
                                sh './gradlew --no-daemon --parallel clean compileJava compileTestJava assemble'
                            }
                            stage(stage_name + 'Reference tests') {
                                sh './gradlew --no-daemon --parallel referenceTest'
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
        }, IntegrationTests: {
            def stage_name = "Integration tests node: "
            node {
                checkout scm
                docker.image(docker_image_dind).withRun('--privileged') { d ->
                    docker.image(build_image).inside("--link ${d.id}:docker") {
                        try {
                            stage(stage_name + 'Prepare') {
                                sh './gradlew --no-daemon --parallel clean compileJava compileTestJava assemble'
                            }
                            stage(stage_name + 'Integration Tests') {
                                sh './gradlew --no-daemon --parallel integrationTest'
                            }
                            stage(stage_name + 'Check Licenses') {
                                sh './gradlew --no-daemon --parallel checkLicenses'
                            }
                            stage(stage_name + 'Check javadoc') {
                                sh './gradlew --no-daemon --parallel javadoc'
                            }
                            stage(stage_name + 'Compile Benchmarks') {
                                sh './gradlew --no-daemon --parallel compileJmh'
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
        }, AcceptanceTests: {
            def stage_name = "Acceptance tests node: "
            node {
                checkout scm
                docker.image(docker_image_dind).withRun('--privileged') { d ->
                    docker.image(build_image).inside("--link ${d.id}:docker") {
                        try {
                            stage(stage_name + 'Prepare') {
                                sh './gradlew --no-daemon --parallel clean compileJava compileTestJava assemble'
                            }
                            stage(stage_name + 'Acceptance Tests') {
                                sh './gradlew --no-daemon --parallel acceptanceTest'
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
        }, DocTests: {
            def stage_name = "Documentation tests node: "
            node {
                checkout scm
                stage(stage_name + 'Build') {
//                Python image version should be set to the same as in readthedocs.yml
//                to make sure we test with the same version that RTD will use
                    def container = docker.image("python:3.7-alpine").inside() {
                        try {
                            sh 'pip install -r docs/requirements.txt'
                            sh 'mkdocs build -s'
                        } catch (e) {
                            throw e
                        }
                    }
                }
            }
        }

        if (env.BRANCH_NAME == "master") {
            def registry = 'https://registry.hub.docker.com'
            def userAccount = 'dockerhub-pegasysengci'
            def imageRepos = 'pegasyseng'
            def imageTag = 'develop'
            parallel KubernetesDockerImage: {
                def stage_name = 'Kubernetes Docker image node: '
                def image = imageRepos + '/pantheon-kubernetes:' + imageTag
                def kubernetes_folder = 'kubernetes'
                def kubernetes_image_build_script = kubernetes_folder + '/build_image.sh'
                def version_property_file = 'gradle.properties'
                def reports_folder = kubernetes_folder + '/reports'
                def dockerfile = kubernetes_folder + '/Dockerfile'
                node {
                    checkout scm
                    unstash 'distTarBall'
                    docker.image(build_image).inside() {
                        stage(stage_name + 'Dockerfile lint') {
                            sh "docker run --rm -i hadolint/hadolint < ${dockerfile}"
                        }
                        stage(stage_name + 'Build image') {
                            sh "${kubernetes_image_build_script} '${image}'"
                        }
                        stage(stage_name + "Test image labels") {
                            shortCommit = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
                            version = sh(returnStdout: true, script: "grep -oE \"version=(.*)\" ${version_property_file} | cut -d= -f2").trim()
                            sh "docker image inspect \
    --format='{{index .Config.Labels \"org.label-schema.vcs-ref\"}}' \
    ${image} \
    | grep ${shortCommit}"
                            sh "docker image inspect \
    --format='{{index .Config.Labels \"org.label-schema.version\"}}' \
    ${image} \
    | grep ${version}"
                        }
                        try {
                            stage(stage_name + 'Test image') {
                                sh "mkdir -p ${reports_folder}"
                                sh "cd ${kubernetes_folder} && bash test.sh ${image}"
                            }
                        } finally {
                            junit "${reports_folder}/*.xml"
                            sh "rm -rf ${reports_folder}"
                        }
                        stage(stage_name + 'Push image') {
                            docker.withRegistry(registry, userAccount) {
                                docker.image(image).push()
                            }
                        }
                    }
                }
            },
            DockerImage: {
                def stage_name = 'Docker image node: '
                def image = imageRepos + '/pantheon:' + imageTag
                node {
                    checkout scm
                    unstash 'distTarBall'
                    docker.image(docker_image_dind).withRun('--privileged') { d ->
                        docker.image(docker_image).inside("-e DOCKER_HOST=tcp://docker:2375 --link ${d.id}:docker") {
                            stage(stage_name + 'build image') {
                                sh "cd docker && cp ../build/distributions/pantheon-*.tar.gz ."
                                pantheon = docker.build(image, "docker")
                            }
                            try {
                                stage('test image') {
                                    sh "apk add bash"
                                    sh "mkdir -p docker/reports"
                                    sh "cd docker && bash test.sh ${image}"
                                }
                            } finally {
                                junit 'docker/reports/*.xml'
                                sh "rm -rf docker/reports"
                            }
                            stage(stage_name + 'push image') {
                                docker.withRegistry(registry, userAccount) {
                                    pantheon.push()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
} catch (e) {
    currentBuild.result = 'FAILURE'
} finally {
    // If we're on master and it failed, notify slack
    if (env.BRANCH_NAME == "master") {
        def currentResult = currentBuild.result ?: 'SUCCESS'
        def channel = '#priv-pegasys-prod-dev'
        if (currentResult == 'SUCCESS') {
            def previousResult = currentBuild.previousBuild?.result
            if (previousResult != null && (previousResult == 'FAILURE' || previousResult == 'UNSTABLE')) {
                slackSend(
                    color: 'good',
                    message: "Pantheon branch ${env.BRANCH_NAME} build is back to HEALTHY.\nBuild Number: #${env.BUILD_NUMBER}\n${env.BUILD_URL}",
                    channel: channel
                )
            }
        } else if (currentBuild.result == 'FAILURE') {
            slackSend(
                color: 'danger',
                message: "Pantheon branch ${env.BRANCH_NAME} build is FAILING.\nBuild Number: #${env.BUILD_NUMBER}\n${env.BUILD_URL}",
                channel: channel
            )
        } else if (currentBuild.result == 'UNSTABLE') {
            slackSend(
                color: 'warning',
                message: "Pantheon branch ${env.BRANCH_NAME} build is UNSTABLE.\nBuild Number: #${env.BUILD_NUMBER}\n${env.BUILD_URL}",
                channel: channel
            )
        }
    }
}
