// ─────────────────────────────────────────────────────────────────────────────
//  Jenkins Pipeline – RHEL Web Server + Java 17 + Demo Service
//
//  Stages:
//    1. Checkout
//    2. Lint         – ansible-lint + YAML syntax
//    3. Syntax Check – ansible-playbook --syntax-check
//    4. Build Image  – build devcontainer Docker image
//    5. Deploy Dev   – run playbook against dev inventory
//    6. Validate Dev – smoke-test HTTP endpoints
//    7. Approval     – manual gate before production
//    8. Deploy Prod  – run playbook against prod inventory
//    9. Validate Prod– smoke-test HTTP endpoints in prod
//   10. Notify       – Slack / email on success or failure
// ─────────────────────────────────────────────────────────────────────────────

pipeline {
    agent {
        docker {
            image 'python:3.12-slim'
            args  '--entrypoint="" -v /var/run/docker.sock:/var/run/docker.sock'
        }
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()
    }

    parameters {
        choice(
            name:        'DEPLOY_TARGET',
            choices:     ['dev', 'prod', 'docker_local'],
            description: 'Target environment to deploy to'
        )
        booleanParam(
            name:         'SKIP_LINT',
            defaultValue: false,
            description:  'Skip ansible-lint (useful for hotfixes)'
        )
        booleanParam(
            name:         'DRY_RUN',
            defaultValue: false,
            description:  'Run with --check --diff (no changes applied)'
        )
        string(
            name:         'ANSIBLE_TAGS',
            defaultValue: '',
            description:  'Comma-separated Ansible tags to run (leave blank for all)'
        )
    }

    environment {
        ANSIBLE_FORCE_COLOR      = '1'
        ANSIBLE_HOST_KEY_CHECKING = 'False'
        // Vault password stored in Jenkins credentials
        VAULT_PASS_FILE          = credentials('ansible-vault-password')
        SSH_KEY                  = credentials('ansible-ssh-key')
        SLACK_CHANNEL            = '#infra-deployments'
        DEPLOY_TIMESTAMP         = sh(script: "date '+%Y%m%d_%H%M%S'", returnStdout: true).trim()
    }

    stages {

        // ── 1. Checkout ──────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log --oneline -5'
            }
        }

        // ── 2. Install tooling ────────────────────────────────────────────────
        stage('Install Tooling') {
            steps {
                sh '''
                    pip install --quiet \
                        ansible==9.* \
                        ansible-lint==6.* \
                        yamllint \
                        jmespath
                    ansible --version
                    ansible-lint --version
                '''
                sh '''
                    cd ansible
                    ansible-galaxy collection install \
                        -r requirements.yml \
                        --force \
                        --no-cache
                '''
            }
        }

        // ── 3. YAML Lint ──────────────────────────────────────────────────────
        stage('YAML Lint') {
            when { expression { !params.SKIP_LINT } }
            steps {
                sh '''
                    yamllint -d '{
                      extends: default,
                      rules: {
                        line-length: {max: 160},
                        truthy: {allowed-values: ["true","false","yes","no"]}
                      }
                    }' ansible/
                '''
            }
            post {
                failure {
                    echo 'YAML lint failed – fix formatting issues before proceeding.'
                }
            }
        }

        // ── 4. Ansible Lint ───────────────────────────────────────────────────
        stage('Ansible Lint') {
            when { expression { !params.SKIP_LINT } }
            steps {
                dir('ansible') {
                    sh 'ansible-lint playbooks/rhel_webserver.yml --profile=production'
                }
            }
        }

        // ── 5. Syntax Check ───────────────────────────────────────────────────
        stage('Syntax Check') {
            steps {
                dir('ansible') {
                    sh '''
                        ansible-playbook \
                            -i inventory/hosts.ini \
                            playbooks/rhel_webserver.yml \
                            --syntax-check \
                            --list-tasks
                    '''
                }
            }
        }

        // ── 6. Build Dev Container Image ──────────────────────────────────────
        stage('Build Dev Container Image') {
            when {
                anyOf {
                    changeset '.devcontainer/**'
                    expression { currentBuild.number == 1 }
                }
            }
            steps {
                sh '''
                    docker build \
                        -t infra-automation-ansible:${BUILD_NUMBER} \
                        -t infra-automation-ansible:latest \
                        .devcontainer/
                '''
            }
        }

        // ── 7. Deploy to Dev ──────────────────────────────────────────────────
        stage('Deploy – Dev') {
            when {
                anyOf {
                    expression { params.DEPLOY_TARGET == 'dev' }
                    expression { params.DEPLOY_TARGET == 'docker_local' }
                }
            }
            steps {
                script {
                    def extraArgs = buildAnsibleArgs()
                    dir('ansible') {
                        sh """
                            ansible-playbook \\
                                -i inventory/hosts.ini \\
                                playbooks/rhel_webserver.yml \\
                                -l ${params.DEPLOY_TARGET} \\
                                --private-key ${SSH_KEY} \\
                                --vault-password-file ${VAULT_PASS_FILE} \\
                                ${extraArgs} \\
                                -v
                        """
                    }
                }
            }
        }

        // ── 8. Validate Dev ───────────────────────────────────────────────────
        stage('Validate – Dev') {
            when {
                anyOf {
                    expression { params.DEPLOY_TARGET == 'dev' }
                    expression { params.DEPLOY_TARGET == 'docker_local' }
                }
            }
            steps {
                script {
                    def host = params.DEPLOY_TARGET == 'docker_local' ? 'localhost:9090' : 'dev-rhel-01'
                    def appHost = params.DEPLOY_TARGET == 'docker_local' ? 'localhost:8080' : 'dev-rhel-01:8080'
                    validateEndpoints(host, appHost)
                }
            }
        }

        // ── 9. Manual Approval Gate ───────────────────────────────────────────
        stage('Approval – Prod Gate') {
            when { expression { params.DEPLOY_TARGET == 'prod' } }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    input(
                        message: "Deploy build #${BUILD_NUMBER} to PRODUCTION?",
                        ok: 'Deploy to Prod',
                        submitter: 'senior-engineers,release-managers'
                    )
                }
            }
        }

        // ── 10. Deploy to Prod ────────────────────────────────────────────────
        stage('Deploy – Prod') {
            when { expression { params.DEPLOY_TARGET == 'prod' } }
            steps {
                script {
                    def extraArgs = buildAnsibleArgs()
                    dir('ansible') {
                        sh """
                            ansible-playbook \\
                                -i inventory/hosts.ini \\
                                playbooks/rhel_webserver.yml \\
                                -l prod \\
                                --private-key ${SSH_KEY} \\
                                --vault-password-file ${VAULT_PASS_FILE} \\
                                ${extraArgs}
                        """
                    }
                }
            }
        }

        // ── 11. Validate Prod ─────────────────────────────────────────────────
        stage('Validate – Prod') {
            when { expression { params.DEPLOY_TARGET == 'prod' } }
            steps {
                script {
                    validateEndpoints('prod-rhel-01', 'prod-rhel-01:8080')
                    validateEndpoints('prod-rhel-02', 'prod-rhel-02:8080')
                }
            }
        }
    }

    post {
        success {
            echo """
            ╔══════════════════════════════════════════════╗
            ║  DEPLOYMENT SUCCESSFUL                       ║
            ║  Build  : #${BUILD_NUMBER}                   ║
            ║  Target : ${params.DEPLOY_TARGET}            ║
            ║  Time   : ${DEPLOY_TIMESTAMP}                ║
            ╚══════════════════════════════════════════════╝
            """
            slackSend(
                channel: env.SLACK_CHANNEL,
                color:   'good',
                message: """:white_check_mark: *Infra Deployment Succeeded*
Build: `#${BUILD_NUMBER}` | Target: `${params.DEPLOY_TARGET}` | Branch: `${GIT_BRANCH}`
<${BUILD_URL}|View Build>"""
            )
        }

        failure {
            echo 'Deployment FAILED. Check the logs above.'
            slackSend(
                channel: env.SLACK_CHANNEL,
                color:   'danger',
                message: """:x: *Infra Deployment Failed*
Build: `#${BUILD_NUMBER}` | Target: `${params.DEPLOY_TARGET}` | Branch: `${GIT_BRANCH}`
<${BUILD_URL}|View Build>"""
            )
        }

        unstable {
            slackSend(
                channel: env.SLACK_CHANNEL,
                color:   'warning',
                message: """:warning: *Infra Deployment Unstable*
Build: `#${BUILD_NUMBER}` | Target: `${params.DEPLOY_TARGET}`
<${BUILD_URL}|View Build>"""
            )
        }

        always {
            cleanWs()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helper functions
// ─────────────────────────────────────────────────────────────────────────────

def buildAnsibleArgs() {
    def args = []
    if (params.DRY_RUN) {
        args << '--check --diff'
    }
    if (params.ANSIBLE_TAGS?.trim()) {
        args << "--tags '${params.ANSIBLE_TAGS}'"
    }
    return args.join(' ')
}

def validateEndpoints(String webHost, String appHost) {
    sh """
        echo '──────────────────────────────────────────────'
        echo ' Validating endpoints on ${webHost}'
        echo '──────────────────────────────────────────────'

        # Apache welcome page
        HTTP_CODE=\$(curl -s -o /dev/null -w '%{http_code}' http://${webHost}/ --max-time 10)
        [ "\$HTTP_CODE" = "200" ] || [ "\$HTTP_CODE" = "403" ] || \\
            (echo "Apache check FAILED (HTTP \$HTTP_CODE)" && exit 1)
        echo "Apache HTTP: \$HTTP_CODE ✓"

        # Demo-app health endpoint
        HEALTH=\$(curl -s http://${appHost}/health --max-time 10)
        echo "Demo-app health: \$HEALTH"
        echo "\$HEALTH" | grep -q '"status":"UP"' || \\
            (echo "Health check FAILED" && exit 1)
        echo "Demo-app health: UP ✓"

        # Demo-app info endpoint
        curl -s http://${appHost}/info --max-time 10 | grep -q '"java"' || \\
            (echo "Info endpoint check FAILED" && exit 1)
        echo "Demo-app info: ✓"

        echo 'All endpoint checks passed.'
    """
}
