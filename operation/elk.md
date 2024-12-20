
>es主节点至少三台，剩下机器的全为es的数据节点
>kibana为一台

>提供软件包：
>jdk.tar.gz
>filebeat-oss-6.8.1-linux-x86_64.tar.gz
>elasticsearch-oss-7.1.1-linux-x86_64.tar.gz
>kibana-oss-7.1.1-linux-x86_64.tar.gz

# 安装jdk
>es需要java环境

将jdk包放至`/app/module`目录下
```shell
mkdir -p /app/module
cd /app/module
# 上传jdk包到此处
tar -xvf jdk.tar.gz
```

脚本：
```shell
echo '
JAVA_HOME=/app/module/jdk
PATH=$JAVA_HOME/bin:$PATH
export JAVA_HOME PATH
' >> /etc/profile
# 重新加载配置
. /etc/profile
```


# ES配置
>先安装jdk

**系统优化**：
```shell
echo '
* soft nofile 65536
* hard nofile 131072
* soft nproc 65536
* hard nproc 131072
' >> /etc/security/limits.conf

echo 'vm.max_map_count=262144' >> /etc/sysctl.conf
sysctl -p
```

将elasticsearch包放至`/app/module`目录下并解压
```shell
mkdir  -p /app/module
cd /app/module
# 将kafka包放至该目录下
tar -xvf elasticsearch-oss-7.1.1-linux-x86_64.tar.gz
```

**配置JVM内存堆大小**：
- 路径：`/app/module/es/config/jvm.options`

	```
	-Xms1g
	-Xmx1g
	```
- 以上内容都修改大小为主机**物理内存的一半**
	**例如**,如果主机物理内存为8g：
	```
	-Xms4g
	-Xmx4g
	```

新建数据存放目录
```shell
mkdir -p /data/es/data
mkdir -p /data/es/logs
```

**节点**配置文件：（`/app/module/es/config/elasticsearch.yml`）
```yaml
cluster.name: elk
node.name: es-1
node.master: true
node.data: false
path.data: /data/es/data
path.logs: /data/es/logs
bootstrap.memory_lock: false
bootstrap.system_call_filter: false
network.host: 7.220.80.75
http.port: 9200
transport.tcp.port: 9300
discovery.seed_hosts: ["7.220.80.75:9300","7.220.81.177:9300","7.220.78.222:9300","7.220.80.51:9300"]
cluster.initial_master_nodes: ["es-1","es-2","es-3"]
http.cors.enabled: true
http.cors.allow-origin: "*"
```
**修改项**（注意空格）：
- node.name需要修改,每个es机器都要不同，es-1、es-2、·········依次类推
- node.master：该项为true时该节点能够通过选举变成主节点。**主节点需要写成true，数据节点写为false**
- node.data：该项为true时该节点能够存储数据。**主节点配置为false，数据节点配置为true**
- network.host修改为**主机ip**
- **discovery.seed_hosts**包含**所有的es节点**，其中的ip地址**更换为实际的ip**，依次类推
- **cluster.initial_master_nodes**是可以变成**主节点**的节点配置，里边加入集群所有的**主节点的节点名字(不是IP)**，需要与上边node.name相对应

**新建es启动用户并修改属主属组**：
```shell
useradd es
echo 'es' | passwd --stdin es
chown -R es.es /app/module/es/
chown -R es.es /data/es
```

**启动es**：
```shell
su - es -c "nohup /app/module/es/bin/elasticsearch &"
```


# kibana配置
将kibana包放至`/app/module`目录下并解压
```shell
mkdir  -p /app/module
cd /app/module
# 将kafka包放至该目录下
tar -xvf kibana-oss-7.1.1-linux-x86_64.tar.gz
```

**修改配置文件**（`/app/module/kibana/config/kibana.yml`）：
```yml
server.port: 5601
server.host: "主机ip"
elasticsearch.hosts: ["http://es1:9200", "http://es2:9200", "http://es3:9200"]
kibana.index: ".kibana"
elasticsearch.requestTimeout: 90000
```
**修改项**（注意空格）：
- server.host为主机ip
- elasticsearch.hosts中为es集群所有**主节点**的ip，更换es1、es2、es3为对应的es集群**主节点**ip

启动命令：
```shell
nohup /app/module/kibana/bin/kibana &
```

# filebeat配置
>安装在需要收集日志的机器上

将filebeat包放至`/app/module`目录下并解压
```shell
mkdir  -p /app/module
cd /app/module
# 将kafka包放至该目录下
tar -xvf filebeat-oss-6.8.1-linux-x86_64.tar.gz
```

修改配置文件(`/app/module/filebeat/filebeat.yml`)：
```yml
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /app/log/*/logs/iptrace.log
filebeat.config.modules:
  path: ${path.config}/modules.d/*.ym
  reload.enabled: false
  
setup.template.settings:
  index.number_of_shards: 1
setup.template.name: "log_log"
setup.template.pattern: "log-*"
setup.template.overwrite: true
setup.template.enabled: true
setup.ilm.enabled: false

setup.kibana:
  host: "7.220.80.223:5601"

output.elasticsearch:
  hosts: ["7.220.80.75:9200","7.220.81.177:9200","7.220.78.222:9200"]
  index: "log-%{+YYYY.MM.dd}"
processors:
  - add_host_metadata:
      netinfo.enabled: true
  - drop_fields:
      fields: ["host.id", "host.mac", "host.name", "beat", "host.os", "prospector", "log", "input",  "offset", "host.architecture", "host.containerized"]
  - drop_event:
      when:
        or:
          - contains:
              message: "INFO"
          - contains:
              message: "DEBUG"
```
**注意项**：
- filebeat.inputs中注意日志文件的路径
- setup.kibana中的host修改为kibana机器的ip，端口为5601
- output.elasticsearch中的hosts修改为es集群中所有主节点的ip
- drop_event为丢弃包含INFO和DEBUG字段的日志信息，可自行决定是否添加。如果日志量非常大，而且只关心特定级别的日志（如ERROR或WARN），这种过滤方式可以显著减少数据传输量，提高系统性能。

**启动命令**：

```shell
nohup /app/module/filebeat/filebeat &
```


# kibana网页端操作
>注意使用chrome系的浏览器，firefox系的浏览器会有问题

创建索引

创建后返回选择该索引即可

# 关闭程序脚本
```shell
#!/bin/bash
APP_NAME="要关闭的程序名"
#first check
pid=`ps -ef| grep "$APP_NAME" | grep -v grep |awk '{print $2}'`
if [ "" != "$pid" ]; then
     echo "stoping ..."
     kill -15 $pid
     sleep 5s
fi
#check again
pid=`ps -ef | grep "$APP_NAME" | grep -v grep |awk '{print $2}'`
if [ "" != "$pid" ]; then
     echo "stop failed, force stop it..."
     kill -9 $pid
fi
```
