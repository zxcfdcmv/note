import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def getParams() {
    return [
        codeName: 'preprocessor',
        gitUrl: 'ssh://git@szv-y.codehub.huawei.com:2222/IPTrace_V200R005_Repository/COMMONS/preprocessor.git',
        ansiblePlaybookPath: '/data/jenkins/HomeLogServer/tasks/main.yml',
        ansibleInventoryPath: '/data/jenkins/HomeLogServer/hosts'
    ]
}

def getBuildEnvParams(params) {
    return [
        buildenv: params.buildenv,
        update_conf: params.update_conf,
        env_ser: params.env_ser,
        env_pro: params.env_pro,
        code_br: params.code_br
    ]
}

def getGitCheckoutSteps(params) {
    return {
        cleanWs()
        checkout changelog: false, poll: false, scm: [
            $class: 'GitSCM',
            branches: [[name: "*/${params.code_br}"]],
            userRemoteConfigs: [[url: params.gitUrl]]
        ]
    }
}

def getBuildSteps(params, workspace) {
    return {
        script {
            def envSerList = params.env_ser.split(',')
            def envProList = params.env_pro.split(',')
            def combinations = []

            envSerList.each { ser ->
                envProList.each { pro ->
                    dir("${workspace}") {
                        def modPath = sh(script: """
                            find ./ -maxdepth 1 -type d -name "*${pro}wlan-${ser}-starter" | sed "s,^\\./,," 
                        """, returnStdout: true).trim()
                        if (modPath) {
                            sh """
                                mvn clean package -Dmaven.test.skip=true -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dassembly.skipAssembly=true -am -pl ${modPath}
                            """
                            combinations.add([ser: ser, pro: pro, modPath: modPath])
                        }
                    }
                }
            }

            env.COMBINATIONS = JsonOutput.toJson(combinations)
            writeFile file: 'combinations.json', text: env.COMBINATIONS
        }
    }
}

def getDeploySteps(params, workspace, combinationsJson) {
    return {
        script {
            def combinations = new JsonSlurper().parseText(combinationsJson).collect { it as HashMap }
            def deployTasks = [:]

            combinations.each { combination ->
                def ser = combination.ser.toString().trim()
                def pro = combination.pro.toString().trim()
                def modPath = combination.modPath.toString().trim()
                def taskName = "更新环境_${params.buildenv}_${pro}_${ser}_${params.code_br}分支"
                def envName = "${params.buildenv}_${pro}_${ser}${params.code_br != 'master' ? "_${params.code_br}" : ''}"

                def playbookCommand = """
                    ansible-playbook ${params.ansiblePlaybookPath} -i ${params.ansibleInventoryPath} -e "codeName=${params.codeName} update_conf=${params.update_conf} envName=${envName} local_jar_path=${workspace}/${modPath}/target modName=${pro}_${ser}"
                """

                deployTasks[taskName] = {
                    sh playbookCommand
                }
            }

            parallel deployTasks
        }
    }
}

pipeline {
    agent any
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }
    environment {
        params = getParams()
    }
    parameters {
        choice(name: 'buildenv', choices: 'dev\ntest', description: '要更新的环境, dev为敏捷环境, test为测试环境')
        choice(name: 'update_conf', choices: 'no\nyes', description: '是否更新配置文件')
        string(name: 'env_ser', defaultValue: 'dpi,nr', description: '请选择更新的业务')
        string(name: 'env_pro', defaultValue: 'qh,zj', description: '请选择更新的省份(青海/浙江)')
        choice(name: 'code_br', choices: 'master\n', description: '使用的代码仓分支')
    }
    stages {
        stage('拉取代码') {
            steps {
                getGitCheckoutSteps(params)
                milestone(label: '拉取代码完成', ordinal: 1)
            }
        }
        stage('构建') {
            steps {
                script {
                    try {
                        getBuildSteps(params, WORKSPACE)
                        milestone(label: '构建完成', ordinal: 2)
                    } catch (Exception e) {
                        error "构建失败: ${e.message}"
                    }
                }
            }
        }
        stage('部署') {
            steps {
                script {
                    try {
                        def combinationsJson = readFile file: 'combinations.json'
                        getDeploySteps(params, WORKSPACE, combinationsJson)
                        milestone(label: '部署完成', ordinal: 3)
                    } catch (Exception e) {
                        error "部署失败: ${e.message}"
                    }
                }
            }
        }
    }
}
