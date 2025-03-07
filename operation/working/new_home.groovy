pipeline {
    agent any
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 45, unit: 'MINUTES')  // 总超时时间
        disableConcurrentBuilds()           // 禁止并发构建
    }
    parameters {
        choice(name: 'buildenv', 
            choices: ['dev', 'test'], 
            description: '部署环境选择（dev=敏捷环境，test=测试环境）')
        
        booleanParam(name: 'update_conf',
            defaultValue: false,
            description: '是否更新配置文件')
        
        extendedChoice(
            name: 'env_ser',
            type: 'CHECK_BOX',
            multiSelectDelimiter: ',',
            value: 'wlan,home',
            description: '选择更新的业务类型'
        )
        
        extendedChoice(
            name: 'env_pro',
            type: 'CHECK_BOX',
            multiSelectDelimiter: ',',
            value: 'sc,qh,xj,zj',
            description: '选择更新的省份（四川=sc，青海=qh，新疆=xj，浙江=zj）'
        )
        
        choice(name: 'code_br',
            choices: ['master', 'FIC65'],
            description: '代码分支选择')
    }
    environment {
        REPO_URL = 'ssh://git@szv-y.codehub.huawei.com:2222/IPTrace_V200R005_Repository/HOMELOGSERVER/HomeLogServer.git'
        ANSIBLE_HOME = '/data/jenkins/HomeLogServer'
        MVN_OPTS = "-Dmaven.test.skip=true -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dassembly.skipAssembly=true"
    }
    stages {
        stage('代码仓库准备') {
            steps {
                cleanWs()
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${params.code_br}"]],
                    extensions: [[
                        $class: 'CloneOption',
                        depth: 1,
                        timeout: 10,
                        noTags: true
                    ]],
                    userRemoteConfigs: [[
                        url: env.REPO_URL,
                        credentialsId: 'gitlab-ssh-key'  // 使用凭证管理
                    ]]
                ])
                milestone(label: '代码克隆完成', ordinal: 1)
            }
        }
        
        stage('多维度构建') {
            options {
                timeout(time: 20, unit: 'MINUTES')  // 阶段级超时
            }
            steps {
                script {
                    // 参数有效性验证
                    validateParameters()
                    
                    // 生成构建矩阵
                    def buildMatrix = generateBuildMatrix(
                        services: params.env_ser.split(','), 
                        provinces: params.env_pro.split(','))
                    
                    // 并行构建执行
                    def buildTasks = [:]
                    buildMatrix.each { combo ->
                        String taskName = "构建_${combo.province}_${combo.service}"
                        buildTasks[taskName] = { executeBuild(combo) }
                    }
                    parallel buildTasks
                    
                    // 保存构建矩阵
                    env.BUILD_MATRIX = JsonOutput.toJson(buildMatrix)
                    archiveArtifacts artifacts: 'combinations.json', onlyIfSuccessful: true
                }
                milestone(label: '全量构建完成', ordinal: 2)
            }
        }
        
        stage('智能部署') {
            options {
                retry(2)  // 失败自动重试
            }
            steps {
                script {
                    def deployMatrix = new JsonSlurper().parseText(env.BUILD_MATRIX)
                    def deployTasks = [:]
                    
                    deployMatrix.each { entry ->
                        String taskName = "部署_${entry.province}_${entry.service}"
                        deployTasks[taskName] = { executeDeploy(entry) }
                    }
                    
                    parallel deployTasks
                }
                milestone(label: '部署完成', ordinal: 3)
            }
        }
    }
    post {
        always {
            script {
                currentBuild.description = "Env:${params.buildenv} | Modules:${params.env_ser} | Provinces:${params.env_pro}"
                cleanWs(cleanWhenAborted: true, cleanWhenFailure: true)
            }
        }
        failure {
            emailext(
                subject: '构建失败: ${JOB_NAME} - ${BUILD_NUMBER}',
                body: '''检查到以下问题：
                    |环境: ${params.buildenv}
                    |分支: ${params.code_br}
                    |错误日志: ${BUILD_URL}console''',
                to: 'devops@example.com'
            )
        }
    }
}

// ---------------------------
// 自定义函数库
// ---------------------------
def validateParameters() {
    def validServices = ['wlan', 'home']
    def validProvinces = ['sc', 'qh', 'xj', 'zj']
    
    params.env_ser.split(',').each { svc ->
        if (!validServices.contains(svc)) {
            error "非法业务类型: ${svc}，有效值：${validServices}"
        }
    }
    
    params.env_pro.split(',').each { prov ->
        if (!validProvinces.contains(prov)) {
            error "非法省份代码: ${prov}，有效值：${validProvinces}"
        }
    }
}

def generateBuildMatrix(Map args) {
    def combinations = []
    args.services.each { service ->
        args.provinces.each { province ->
            def modulePath = findModulePath(province, service)
            if (modulePath) {
                combinations << [
                    province: province,
                    service: service,
                    modulePath: modulePath
                ]
            }
        }
    }
    return combinations
}

def findModulePath(String province, String service) {
    dir("${WORKSPACE}/ssf-iptrace") {
        try {
            // 使用预定义模式替代find
            def pattern = "${province}-${service}*-portal*"
            def paths = sh(
                script: "ls -d ${pattern} 2>/dev/null",
                returnStdout: true
            ).trim().split('\n')
            
            return paths ? paths[0] : null
        } catch (Exception e) {
            echo "未找到模块: ${pattern}"
            return null
        }
    }
}

def executeBuild(Map config) {
    dir("${WORKSPACE}/ssf-iptrace") {
        // 动态修改配置
        sh """
            sed -i -E 's#(ssf-iptrace/).*-starters/.*-portal.*(/src)#\\1${config.modulePath}\\2#g' \
                ../aui-sercurityframe/vue.config.js
        """
        
        // 前端构建
        dir('../aui-sercurityframe') {
            sh 'yarn install --frozen-lockfile'
            sh 'yarn build --mode production'
        }
        
        // 后端构建
        sh "mvn clean package ${env.MVN_OPTS} -am -pl ${config.modulePath}"
    }
}

def executeDeploy(Map config) {
    def envSuffix = params.code_br == 'master' ? 
        "${params.buildenv}_${config.province}_${config.service}" :
        "${params.buildenv}_${config.province}_${config.service}_${params.code_br}"
    
    sh """
        ansible-playbook ${env.ANSIBLE_HOME}/tasks/main.yml \
            -i ${env.ANSIBLE_HOME}/hosts \
            -e "update_conf=${params.update_conf} \
                envName=${envSuffix} \
                local_jar_path=${WORKSPACE}/ssf-iptrace/${config.modulePath}/target \
                modName=${config.province}_${config.service}"
    """
}
