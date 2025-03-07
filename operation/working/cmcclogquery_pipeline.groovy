import groovy.json.JsonOutput
import groovy.json.JsonSlurper
pipeline {
    agent any
    options {
        buildDiscarder(logRotator(
            numToKeepStr: '10'    // 最多保留10次构建
        ))    
    }   
    environment {
        // 定义全局环境变量
        codeName = 'CMCCLOGQUERY'
    }    
    parameters {
        choice(name: 'buildenv', choices: 'dev\ntest', description: '要更新的环境, dev为敏捷环境, test为测试环境')
        choice(name: 'update_conf', choices: 'no\nyes', description: '是否更新配置文件')
        string(name: 'env_mod', defaultValue: 'httpif,query,queryM,upload,uploadM,uploadResult', description: '请选择更新的模块')
        choice(name: 'code_br', choices: 'master\n', description: '使用的代码仓分支')
    }
    stages {
        stage('拉取代码') {
            steps {
                cleanWs()
                checkout changelog: false, poll: false, scm: [
                    $class: 'GitSCM',
                    branches: [[name: "*/${params.code_br}"]],
                    userRemoteConfigs: [[url: 'ssh://git@szv-y.codehub.huawei.com:2222/IPTrace_V200R005_Repository/CMCCLOGQUERY.git']]
                ]            
                milestone(label: '拉取代码完成', ordinal: 1)
            }
        }
        stage('构建') {
            steps {
                script {
                    def envModList = params.env_mod.split(',')
                    def combinations = []
                    
                    dir("${WORKSPACE}/aui-sercurityframe") {
                        sh 'yarn install'
                        sh 'yarn run build'
                    }

                    envModList.each { mod ->
                        
                        dir("${WORKSPACE}/ssfportal") {
                            def modPath = sh(script: """
                                find ./ -maxdepth 2 -type d -name "${mod}Starter" | sed "s,^\\./,," 
                            """, returnStdout: true).trim()
                        
                            sh "mvn clean package -Dmaven.test.skip=true -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dassembly.skipAssembly=true -am -pl ${modPath}"
                        }
                        combinations.add([mod: mod, modPath: modPath])
                        
                    }
                    env.COMBINATIONS = JsonOutput.toJson(combinations)
                    // 将combinations列表保存到Pipeline的环境变量中
                    echo "${COMBINATIONS}"
                    // 将COMBINATIONS写入文件
                    writeFile file: 'combinations.json', text: env.COMBINATIONS                    
                }
                milestone(label: '构建完成', ordinal: 2)
            }
        }
        stage('部署') {
            steps {
                script {
                    // 从文件中读取COMBINATIONS
                    def combinationsJson = readFile file: 'combinations.json'                    
                    echo "${COMBINATIONS}"
                    def combinations = new JsonSlurper().parseText(combinationsJson).collect { it as HashMap }
                    echo "${combinations}"
                    def deployTasks = [:]
                    if (params.code_br == "master") {
                        combinations.each { combination ->
                            def mod = combination.mod.toString().trim()
                            def modPath = combination.modPath.toString().trim()
                            def taskName = "更新环境_${params.buildenv}_${params.code_br}分支"
                            def playbookCommand = """
                                ansible-playbook /data/jenkins/HomeLogServer/main.yml -i /data/jenkins/HomeLogServer/hosts -e "codeName=${params.codeName} update_conf=${params.update_conf} envName=${params.buildenv}  local_jar_path=${WORKSPACE}/ssfportal/${modPath}/target modName=${mod}"
                            """
                            deployTasks[taskName] = {
                                sh playbookCommand
                            }
                        }
                    } else {
                        combinations.each { combination ->
                            def modPath = combination.modPath.toString().trim()
                            def taskName = "更新环境_${params.buildenv}_${params.code_br}分支"
                            def playbookCommand = """
                                ansible-playbook /data/jenkins/HomeLogServer/main.yml -i /data/jenkins/HomeLogServer/hosts -e "codeName=${params.codeName} update_conf=${params.update_conf} envName=${params.buildenv}_${params.code_br}  local_jar_path=${WORKSPACE}/ssfportal/${modPath}/target modName=${mod}"
                            """
                            
                            deployTasks[taskName] = {
                                sh playbookCommand
                            }
                        }
                    }                    
                    parallel deployTasks
                }
                milestone(label: '部署完成', ordinal: 3)
            }
        }
    }
}
