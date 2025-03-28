@Library('sendMessage') _

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
pipeline {
    agent any
    options {
        quietPeriod(600)     // 延迟至10分钟后构建
        timeout(time: 10, unit: 'MINUTES')  // 总超时时间
        disableConcurrentBuilds()           // 禁止并发构建
        buildDiscarder(logRotator(numToKeepStr: '10')) // 最多保留10次构建
    }   
    environment {
        CODE_NAME = 'CMCCLOGQUERY' // 一定要与代码仓名字对齐
        REPO_URL = 'ssh://git@szv-y.codehub.huawei.com:2222/IPTrace_V200R005_Repository/CMCCLOGQUERY.git'
        ANSIBLE_HOME = '/app/jenkins/home/jobs/CI/workspace/jenkins/codeConfig'
        MVN_OPTS = "-Dmaven.test.skip=true -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dassembly.skipAssembly=true"
        idFilePath = "last_commit_id_${params.buildenv}"
    }
    
    parameters {
        choice(name: 'buildenv', choices: 'dev\ntest', description: '要更新的环境, dev为敏捷环境, test为测试环境')
        string(name: 'env_mod', defaultValue: 'httpif,query,queryM,upload,uploadM,uploadResult', description: '请选择更新的模块(提新代码后自动触发构建针对敏捷环境此环境变量不会生效, 其他场景都会生效包括冒烟环境)')
        choice(name: 'envBr', choices: ['master'], description: '该变量用于处理手动触发流水线时的场景, (env.codehubSourceBranch环境变量只能在自动触发流水线时获取)')
    }
    stages {
        stage('可构建分支检查') {
            steps {
                script {
                    env.codeBr = env.codehubSourceBranch ?: params.envBr
                    
                    // 检查当前分支是否在允许的列表中
                    def condition = env.codeBr in params.envBr
                    
                    if (!condition) {
                        // 终止流水线并设置结果为ABORTED
                        currentBuild.result = 'ABORTED'
                        error("分支 ${env.codeBr} 没有可用环境, 流水线停止")
                    }                
                    
                }
                milestone(label: '检查分支完成', ordinal: 1)
            }
        }    
    }
    // 根据分支动态设置工作目录    
    ws("${WORKSPACE}/${env.codeBr}") {
        stages {
            stage('拉取代码') {
                steps {
                    checkout changelog: false, poll: false, scm: [
                        $class: 'GitSCM',
                        branches: [[name: "origin/${env.codeBr}"]],
                        userRemoteConfigs: [[url: env.REPO_URL]],
                        extensions: [[
                            $class: 'CloneOption',
                            noTags: true
                        ]],
                    ]
                    script {
                        // 获取当前提交ID
                        env.CURRENT_COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                    }
                    milestone(label: '拉取代码完成', ordinal: 2)
                }
            }
            stage('检查文件变更并更新配置文件') {
                steps {
                    script {
                        if (fileExists(env.idFilePath)) {
                            // 读取上一次构建的提交ID
                            env.LAST_COMMIT_ID = readFile(env.idFilePath).trim()
                            if (env.CURRENT_COMMIT_ID != env.LAST_COMMIT_ID) {
                                // 获取上次提交ID到当前提交ID之间的文件变更列表
                                def changedFiles = sh(returnStdout: true, script: "git diff --name-only ${env.LAST_COMMIT_ID} ${env.CURRENT_COMMIT_ID}").trim().split('\n') 
                                // 判断是否有变更文件, 如果有则执行以下检查文件的操作, 否则跳过
                                if (changedFiles) {
                                    // 用于存储需要修改的模块名
                                    def modulesToModify = []                        
                                    def modifyFiles = []
                                    
                                    // 遍历变更文件列表
                                    changedFiles.each { file ->
                                        if (params.buildenv == 'dev') {
                                            if (file.contains('ssf-iptrace-cmcclogquery')) {
                                                def parts = file.split('ssf-iptrace-cmcclogquery/')
                                                def updateModule = parts[1].split('/')[0]
                                                updateModule = modifyModuleName(updateModule)
                                                if (env.env_mod.contains(updateModule)) {
                                                    modulesToModify.add(updateModule)
                                                }
                                            }
                                        }
                                        // 检查文件路径是否包含 "resources"
                                        if (file.contains('resources')) {
                                            // 提取模块名和配置文件名
                                            def parts = file.split('resources/')
                                            def updateModule = parts[0].split('/')[-3]
                                            def updateConfig = parts[1]
                                            if (params.buildenv == 'dev') {
                                                sendMessage(
                                                    content: """
                                                        |<span style='color:red;font-weight:bold;'>配置文件需要更新</span>\n
                                                        |模块名: ${updateModule}\n
                                                        |配置文件名: ${updateConfig}
                                                    """.stripMargin()
                                                )                                    
                                            }
                                            modifyFiles.add([updateModule: updateModule, updateConfig: updateConfig])
                                        }
                                        
                                    }
                                    if (modifyFiles) {
                                        // writeFile file: 'pipeline2ModifyFiles', text: JsonOutput.toJson(modifyFiles)
                                        env.MODIFYFILES = JsonOutput.toJson(modifyFiles)                                        
                                        stage('配置文件更新') {
                                            steps {
                                                input message: '请确认是否继续部署？', ok: '继续部署'
                                                script {
                                                    // def modifyFilesJson = readFile file: 'pipeline2ModifyFiles'
                                                    // def modifyFiles = new JsonSlurper().parseText(modifyFilesJson).collect { it as HashMap }
                                                    def modifyFiles = new JsonSlurper().parseText(env.MODIFYFILES).collect { it as HashMap }
                                                    def configTasks = [:]
                                                    modifyFiles.each { modifyFile ->
                                                        def updateModule = modifyFile.updateModule.toString().trim()
                                                        def updateConfig = modifyFile.updateConfig.toString().trim()
                                                        def taskName = "更新配置文件_${params.buildenv}_${updateModule}_${updateConfig}_${env.codeBr}分支"
                                                        def playbookCommand = """
                                                            ansible-playbook ${env.ANSIBLE_HOME}/tasks/updateConf.yml \
                                                                -i ${env.ANSIBLE_HOME}/${env.CODE_NAME}/hosts \
                                                                -e "codeName=${env.CODE_NAME} \
                                                                    envName=${params.buildenv}_${env.codeBr} \
                                                                    updateConfig=${updateConfig} \
                                                                    // 用于远程服务器路径当中, 作为额外的路径参数
                                                                    extraPath=${updateModule}"
                                                        """
                                                        configTasks[taskName] = {
                                                            sh playbookCommand
                                                        }
                                                    }
                                                    parallel configTasks
                                                }
                                                milestone(label: '配置文件更新完成', ordinal: 2)
                                            }
                                        }
                                        
                                    }
                                    env.MODULESTOMODIFY = modulesToModify ? modulesToModify.join(',') : params.env_mod
                                    // writeFile file: 'pipeline2ModulesToModify', text: env.MODULESTOMODIFY
                                }
                            }
                        }
                    }
                    milestone(label: '检查文件变更完成', ordinal: 3)
                }
            }
            stage('构建') {
                steps {
                    script {
                        // def modulesToModify = readFile file: 'pipeline2ModulesToModify'                    
                        def envModList = env.MODULESTOMODIFY.split(',')
                        
                        def buildMods = []
                        
                        // 检查 envModList 是否包含 "httpif"
                        if (envModList.contains('httpif')) {
                            dir("${WORKSPACE}/aui-sercurityframe") {
                                sh """
                                    sed -i 's/"core-js": "3.36.0"/"core-js": "^3.36.0"/g' package.json
                                """
                                sh 'yarn install'
                                sh 'yarn run build'
                            }
                        }                    
                        
                        envModList.each { mod ->
                            dir("${WORKSPACE}/ssfportal") {
                                def modPath = sh(script: """
                                    find ./ -maxdepth 2 -type d -name "${mod}Starter" | sed "s,^\\./,," 
                                """, returnStdout: true).trim()
                                
                                if (modPath) {
                                    sh "mvn clean package ${MVN_OPTS} -am -pl ${modPath}"
                                    buildMods.add([mod: mod, modPath: modPath])
                                }                        
                            }
                        }

                        // 将buildMods写入文件
                        // writeFile file: 'pipeline3buildMods', text: JsonOutput.toJson(buildMods)
                        env.BUILDMODS = JsonOutput.toJson(buildMods)                                        
                    }
                    milestone(label: '构建完成', ordinal: 5)
                }
            }
            stage('部署') {
                steps {
                    script {
                        // 从文件中读取COMBINATIONS
                        // def buildModsJson = readFile file: 'pipeline3buildMods'                    
                        // echo "${COMBINATIONS}"
                        def buildMods = new JsonSlurper().parseText(env.BUILDMODS).collect { it as HashMap }
                        // echo "${combinations}"
                        def deployTasks = [:]
                        buildMods.each { buildMod ->
                            def mod = buildMod.mod.toString().trim()
                            def modPath = buildMod.modPath.toString().trim()
                            def taskName = "更新环境_${params.buildenv}_${mod}_${env.codeBr}分支"
                            def playbookCommand = """
                                ansible-playbook ${env.ANSIBLE_HOME}/tasks/update_jar.yml \
                                    -i ${env.ANSIBLE_HOME}/${env.CODE_NAME}/hosts \
                                    -e "codeName=${env.CODE_NAME} \
                                        envName=${params.buildenv}_${env.codeBr} \
                                        local_jar_path=${WORKSPACE}/${env.codeBr}/ssfportal/${modPath}/target \
                                        // 用于远程服务器路径当中, 作为额外的路径参数
                                        extraPath=${mod}"
                            """
                            deployTasks[taskName] = {
                                sh playbookCommand
                            }
                        }
                        parallel deployTasks
                    }
                    milestone(label: '部署完成', ordinal: 6)
                }
            }
        }
        post {
            always {
                script {
                    writeFile file: env.idFilePath, text: env.CURRENT_COMMIT_ID
                }
            }
            success {
                script {
                    def modNameList = params.buildenv == 'dev' ? 
                        env.MODULESTOMODIFY :
                        params.env_mod
                    sendMessage(
                        content: """
                            |<span style='color:green;font-weight:bold;'>环境更新成功</span>\n
                            |${env.CODE_NAME}_${params.buildenv}
                            |模块名: ${modNameList}
                            |更新链接: ${BUILD_URL}console
                        """.stripMargin()
                    )                                    
                }
            }
            failure {
                script {
                    def modNameList = params.buildenv == 'dev' ? 
                        env.MODULESTOMODIFY :
                        params.env_mod
                    sendMessage(
                        content: """
                            |<span style='color:red;font-weight:bold;'>环境更新失败</span>\n
                            |${env.CODE_NAME}_${params.buildenv}\n
                            |模块名: ${modNameList}\n
                            |更新链接: ${BUILD_URL}console
                        """.stripMargin()
                    )                                    
                }
            }
        }
    }  
}

// 定义一个函数来修改模块名
def modifyModuleName(subModule) {
    switch (subModule) {
        case 'query-m':
            return 'queryM'
        case 'upload-m':
            return 'uploadM'
        case 'result-upload':
            return 'uploadResult'
        default:
            return subModule
    }
}
