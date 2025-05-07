import groovy.json.JsonOutput
import groovy.json.JsonSlurper
pipeline {
    agent any
    tools {
        // 这里的名称需与全局配置中的名称一致
        jdk 'jdk8'
    }    
    options {
        quietPeriod(600)     // 延迟至10分钟后构建
        timeout(time: 10, unit: 'MINUTES')  // 总超时时间
        disableConcurrentBuilds()           // 禁止并发构建
        buildDiscarder(logRotator(numToKeepStr: '10')) // 最多保留10次构建
    }   
    environment {
        CODE_NAME = 'CMCCLOGQUERY' // 一定要与代码仓名字一致
        REPO_URL = 'ssh://git@szv-y.codehub.huawei.com:2222/IPTrace_V200R005_Repository/CMCCLOGQUERY.git'
        CI_HOME = '/app/jenkins/home/jobs/CI/workspace/jenkins'
        MVN_OPTS = "-Dmaven.test.skip=true -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dassembly.skipAssembly=true"
        idFilePath = "last_commit_id_${params.buildenv}"
        env_src = '/opt/platform/project'
        env_dest = '/data/jenkins/coverPlatform'
    }
    
    parameters {
        choice(name: 'buildenv', choices: 'dev\ntest', description: '要更新的环境, dev为敏捷环境, test为冒烟环境')
        string(name: 'env_mod', defaultValue: 'httpif,query,queryM,upload,uploadM,uploadResult', description: '请选择更新的模块(提新代码后自动触发构建针对敏捷环境此环境变量不会生效, 其他场景都会生效包括冒烟环境)')
        choice(name: 'envBr', choices: ['master'], description: '该变量用于处理手动触发流水线时的场景, (env.codehubSourceBranch环境变量只能在自动触发流水线时获取)')
        booleanParam(name: 'updateConfAll', defaultValue: false, description: '选中为全量更新配置文件, 不选中为增量更新配置文件(当有配置文件更新时), 默认为不选中')
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
                    } else {
                        def content = """
                            |<span style='color:#FF8C00;font-weight:bold;'>环境开始更新</span>
                            |环境: ${env.CODE_NAME}_${params.buildenv}
                            |分支: ${env.codeBr}
                            |链接: ${BUILD_URL}console
                        """.stripMargin().trim().toString()
                        
                        sh """
                            python3 ${CI_HOME}/pubScript/sendMessage.py "${content}"
                        """
                        
                    }
                    
                }
                milestone(label: '检查分支完成', ordinal: 1)
            }
        }    
    
        stage('拉取代码') {
            steps {
                dir("${WORKSPACE}/${env.codeBr}") {
                
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
                        
                }
                milestone(label: '拉取代码完成', ordinal: 2)
            }
        }
        stage('检查文件变更') {
            steps {
                dir("${WORKSPACE}/${env.codeBr}") {
                    script {
                        if (fileExists(env.idFilePath)) {
                            // 读取上一次构建的提交ID
                            env.LAST_COMMIT_ID = readFile(env.idFilePath).trim()
                            if (env.CURRENT_COMMIT_ID != env.LAST_COMMIT_ID) {
                                // 获取上次提交ID到当前提交ID之间的文件变更列表
                                def changedFiles = sh(returnStdout: true, script: "git diff --name-only ${env.LAST_COMMIT_ID} ${env.CURRENT_COMMIT_ID}").trim().split('\n') 
                                // 判断是否有变更文件, 如果有则执行以下检查文件的操作, 否则跳过
                                if (changedFiles) {
                                    def modifyFiles = []
                                    
                                    // 遍历变更文件列表
                                    changedFiles.each { file ->
                                        // 检查文件路径是否包含 "resources"
                                        if (file.contains('ssfportal-cmcclogquery-starter') && file.contains('resources')) {
                                            // 提取模块名和配置文件名
                                            def parts = file.split('resources/')
                                            // def updateModule = parts[0].split('/')[-3]
                                            def updateModule = parts[0].split('/')[-3].replaceAll(/Starter$/, '')
                                            def updateConfig = parts[1]
                                            modifyFiles.add([updateModule: updateModule, updateConfig: updateConfig])
                                        }
                                        
                                    }
                                    if (modifyFiles) {
                                        // 构建配置变更信息字符串
                                        def configUpdates = modifyFiles.collect { entry ->
                                            "- 模块名: ${entry.updateModule}  配置文件名: ${entry.updateConfig}"
                                        }.join('\n')
                                        
                                        // 向welink发送消息
                                        if (params.buildenv == 'dev') {
                                            def content = """
                                                |<span style='color:blue;font-weight:bold;'>配置文件需要更新</span>
                                                |代码仓: ${env.CODE_NAME}
                                                |待更新的配置文件:
                                                |${configUpdates}
                                            """.stripMargin().trim().toString()
                                            
                                            sh """
                                                python3 ${CI_HOME}/pubScript/sendMessage.py "${content}"
                                            """
                                        }
                                        // 需要更新的配置文件
                                        env.MODIFYFILES = JsonOutput.toJson(modifyFiles)                                        
                                    }
                                }
                            }
                        }
                    }
                }
            
                milestone(label: '检查文件变更完成', ordinal: 3)
            }
        }
        stage('配置文件更新') {
            when {
                expression { 
                    return env.MODIFYFILES || (params.updateConfAll == true) 
                }
            }            
            steps {
                dir("${WORKSPACE}/${env.codeBr}") {
                    script {
                        def configTasks = [:]
                        
                        // 统一处理两种情况的文件列表
                        def modifyFileList = params.updateConfAll ? 
                            params.env_mod.split(',').collect { [updateModule: it, updateConfig: ''] } : 
                            readJSON(text: env.MODIFYFILES)  
                        modifyFileList.each { item ->
                            // 统一提取参数
                            def updateModule = item.updateModule?.trim()
                            def updateConfig = item.updateConfig?.trim()
                            
                            // 动态生成任务名称
                            def taskNameComponents = [
                                "更新配置文件",
                                params.buildenv,
                                updateModule,
                                updateConfig ?: null,  // 当updateConfig存在时才显示
                                "${env.codeBr}分支"
                            ].findAll { it != null }
                            
                            // 构建ansible参数
                            def ansibleVars = [
                                "codeName=${env.CODE_NAME}",
                                "envName=${params.buildenv}_${env.codeBr}",
                                "ciHome=${env.CI_HOME}",
                                "updatePath=${WORKSPACE}/${env.codeBr}",
                                "updateModule=${updateModule}",
                                "updateConfig=${updateConfig}"
                            ].findAll { it != null }
                            def taskName = taskNameComponents.join('_')
                            def playbookCommand = """
                                ansible-playbook ${env.CI_HOME}/codeConfig/tasks/updateConf.yml \
                                    -i ${env.CI_HOME}/codeConfig/${env.CODE_NAME}/hosts \
                                    -e "${ansibleVars.join(' ')}"
                            """
                            configTasks[taskName] = { sh playbookCommand }
                        }
                        parallel configTasks
                    }
                }
                milestone(label: '配置文件更新完成', ordinal: 4)
            }
        }            
        stage('构建') {
            steps {
                dir("${WORKSPACE}/${env.codeBr}") {
                    script {
                        // def modulesToModify = readFile file: 'pipeline2ModulesToModify'                    
                        def envModList = params.env_mod.split(',')
                        
                        def buildMods = []
                        def coverMods = []
                        
                        def coverIP = ""
                        if (params.buildenv == 'dev') {
                            sh """
                                scp -o StrictHostKeyChecking=no root@7.220.10.77:${env.env_src}/env.yml ${env.env_dest}/env.yml
                            """
                            coverIP = sh(
                                script: """
                                    grep '^coverIP=' ${env.CI_HOME}/codeConfig/${env.CODE_NAME}/hosts | head -1 | cut -d= -f2
                                """,
                                returnStdout: true
                            ).trim()                            
                        }
                        
                        // 检查 envModList 是否包含 "httpif"
                        if (envModList.contains('httpif')) {
                            dir("aui-sercurityframe") {
                                sh """
                                    sed -i 's/"core-js": "3.36.0"/"core-js": "^3.36.0"/g' package.json
                                """
                                sh 'yarn install --no-progress --non-interactive'
                                sh 'yarn run build'
                            }
                        }                    

                        envModList.each { mod ->
                            dir("ssfportal") {
                                def modPath = sh(script: """
                                    find ./ -maxdepth 2 -type d -name "${mod}Starter" | sed "s,^\\./,," 
                                """, returnStdout: true).trim()
                                
                                if (modPath) {
                                    sh "mvn clean package ${MVN_OPTS} -am -pl ${modPath}"
                                    buildMods.add([mod: mod, modPath: modPath])
                                    
                                    if (params.buildenv == 'dev') {
                                        def coverUpdate = sh(
                                            script: """
                                              python3 ${env.CI_HOME}/pubScript/coverUpdate.py ${env.env_dest}/env.yml \
                                                --project IPTrace \
                                                --repo "${CODE_NAME}" \
                                                --module "${mod}" \
                                                --ip "${coverIP}" \
                                              > cover.log 2>&1 
                                            """,
                                            returnStatus: true
                                        )                                    
                                        if (coverUpdate == 0) {
                                            def output = readFile('cover.log')
                                            def content = """
                                                |<span style='color:blue;font-weight:bold;'>覆盖率环境新增模块</span>
                                                |${output}
                                            """.stripMargin().trim().toString()
                                            
                                            sh """
                                                python3 ${CI_HOME}/pubScript/sendMessage.py "${content}"
                                            """
                                        }
                                        coverMods.add([mod: mod, modPath: modPath, coverUpdate: coverUpdate])
                                    }
                                    
                                }                        
                            }
                        }
                        if (params.buildenv == 'dev') {
                            sh """
                                scp -o StrictHostKeyChecking=no ${env.env_dest}/env.yml root@7.220.10.77:${env.env_src}/env.yml
                                ssh root@7.220.10.77 "cd /opt/platform/bin; sh start.sh"
                            """
                            env.COVERMODS = JsonOutput.toJson(coverMods)
                        }

                        env.BUILDMODS = JsonOutput.toJson(buildMods)
                    }
                }
            
                milestone(label: '构建完成', ordinal: 5)
            }
        }
        stage('部署') {
            steps {
                dir("${WORKSPACE}/${env.codeBr}") {
                    script {
                        def buildMods = new JsonSlurper().parseText(env.BUILDMODS).collect { it as HashMap }
                        def deployTasks = [:]
                        buildMods.each { buildMod ->
                            def mod = buildMod.mod.toString().trim()
                            def modPath = buildMod.modPath.toString().trim()
                            def taskName = "更新环境_${params.buildenv}_${mod}_${env.codeBr}分支"
                            deployTasks[taskName] = {
                                ansiblePlaybook(
                                    playbook: "${env.CI_HOME}/codeConfig/tasks/update_jar.yml",
                                    inventory: "${env.CI_HOME}/codeConfig/${env.CODE_NAME}/hosts",
                                    extraVars: [
                                        codeName: env.CODE_NAME,
                                        envName: "${params.buildenv}_${env.codeBr}",
                                        local_jar_path: "${WORKSPACE}/${env.codeBr}/ssfportal/${modPath}/target",
                                        extraPath: mod,
                                    ]
                                )
                            }
                        }
                        parallel deployTasks
                    }
                }
                milestone(label: '部署完成', ordinal: 6)
            }
        }
        
        stage('覆盖率环境更新') {
            when {
                expression { 
                    return params.buildenv == 'dev' 
                }
            }            
            steps {
                dir("${WORKSPACE}/${env.codeBr}") {
                    script {
                        def coverMods = new JsonSlurper().parseText(env.COVERMODS).collect { it as HashMap }
                        def coverTasks = [:]
                        coverMods.each { coverMod ->
                            def mod = coverMod.mod.toString().trim()
                            def modPath = coverMod.modPath.toString().trim()
                            def coverUpdate = coverMod.coverUpdate.toString().trim()
                            def taskName = "更新覆盖率环境_${mod}_${env.codeBr}分支"
                            coverTasks[taskName] = {
                                ansiblePlaybook(
                                    playbook: "${env.CI_HOME}/codeConfig/tasks/updateCover.yml",
                                    inventory: "${env.CI_HOME}/codeConfig/${env.CODE_NAME}/hosts",
                                    extraVars: [
                                        codeName: env.CODE_NAME,
                                        local_jar_path: "${WORKSPACE}/${env.codeBr}/ssfportal/${modPath}/target",
                                        extraPath: mod,
                                        repoURL: env.REPO_URL,
                                        coverUpdate: coverUpdate,
                                        env_src: env.env_src,
                                    ]
                                )
                            }                            
                        }
                        parallel coverTasks
                    }
                }
                milestone(label: '覆盖率环境更新完成', ordinal: 7)
            }
        }
        
    }
    post {
        success {
            dir("${WORKSPACE}/${env.codeBr}") {
                script {
                    if (env.CURRENT_COMMIT_ID) {
                        writeFile file: env.idFilePath, text: env.CURRENT_COMMIT_ID
                    }
                
                    def content = """
                        |<span style='color:green;font-weight:bold;'>环境更新成功</span>
                        |环境: ${env.CODE_NAME}_${params.buildenv}
                        |分支: ${env.codeBr}
                        |更新的模块: ${params.env_mod}
                        |链接: ${BUILD_URL}console
                    """.stripMargin().trim().toString()
                    
                    sh """
                        python3 ${CI_HOME}/pubScript/sendMessage.py "${content}"
                    """
                }
            }
        }
        failure {
            script {
                def content = """
                    |<span style='color:red;font-weight:bold;'>环境更新失败</span>
                    |环境: ${env.CODE_NAME}_${params.buildenv}
                    |分支: ${env.codeBr}
                    |更新的模块: ${params.env_mod}
                    |链接: ${BUILD_URL}console
                """.stripMargin().trim().toString()
                
                sh """
                    python3 ${CI_HOME}/pubScript/sendMessage.py "${content}"
                """
            }
        }
    }
}
