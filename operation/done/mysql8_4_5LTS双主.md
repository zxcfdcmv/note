# 生成证书文件
使用一台CA服务器为mysql服务器颁发证书文件
1. `CA端`生成ca证书文件:
    ```bash
    openssl genrsa -out ca-key.pem 2048
    openssl req -x509 -new -nodes -key ca-key.pem -sha256 -days 3650 -out ca.pem -subj "/CN=MySQL-CA"
    ```
1. 客户端:
    ```bash
    # 客户端A执行以下命令:
    openssl genrsa -out server-key.pem 2048
    openssl req -new -key server-key.pem -out server-req.pem -subj "/CN=mysql-A"
    openssl genrsa -out repl-key.pem 2048
    openssl req -new -key repl-key.pem -out repl-req.pem -subj "/CN=repl-A"

    # 客户端B执行以下命令:
    openssl genrsa -out server-key.pem 2048
    openssl req -new -key server-key.pem -out server-req.pem -subj "/CN=mysql-B"
    openssl genrsa -out repl-key.pem 2048
    openssl req -new -key repl-key.pem -out repl-req.pem -subj "/CN=repl-B"
    ```
1. 将客户端生成的req证书文件上传到`CA端`
    ```bash
    # 客户端A:
    scp server-req.pem 'CA端用户名'@'CA端用户名密码':证书颁发目录/server-A-req.pem
    scp repl-req.pem 'CA端用户名'@'CA端用户名密码':证书颁发目录/repl-A-req.pem

    # 客户端B:
    scp server-req.pem 'CA端用户名'@'CA端用户名密码':证书颁发目录/server-B-req.pem
    scp repl-req.pem 'CA端用户名'@'CA端用户名密码':证书颁发目录/repl-B-req.pem
    ```

1. `CA端`为客户端颁发证书文件
    ```bash
    openssl x509 -req -in server-A-req.pem -CA ca.pem -CAkey ca-key.pem -CAcreateserial -out server-A-cert.pem -days 3650 -sha256
    openssl x509 -req -in server-B-req.pem -CA ca.pem -CAkey ca-key.pem -CAcreateserial -out server-B-cert.pem -days 3650 -sha256
    openssl x509 -req -in repl-A-req.pem -CA ca.pem -CAkey ca-key.pem -CAcreateserial -out repl-A-cert.pem -days 3650 -sha256
    openssl x509 -req -in repl-B-req.pem -CA ca.pem -CAkey ca-key.pem -CAcreateserial -out repl-B-cert.pem -days 3650 -sha256
    ```
1. 将以下证书文件下发到客户端
	- 下发到客户端A: `ca.pem`/`server-A-cert.pem`/`repl-A-cert.pem`
	- 下发到客户端B: `ca.pem`/`server-B-cert.pem`/`repl-B-cert.pem`

# 配置`my.cnf`
`vim /etc/my.cnf`
>注意:
MySQL 8.4 LTS 之后该参数已被弃用
default_authentication_plugin=mysql_native_password
## 客户端A
```bash
[mysqld]
basedir=/app/module/mysql-8.4.5-linux-glibc2.28-x86_64
datadir=/data/mysql
socket=/data/mysql/mysql.sock
pid-file=/data/mysql/mysqld.pid
server-id=1
log-bin=mysql-bin
relay-log=mysql-relay-bin
auto-increment-increment=2
auto-increment-offset=1
binlog_format=ROW
gtid_mode=ON
enforce_gtid_consistency=ON
lower_case_table_names=1
port=3306
log-error=/data/mysql/log/mysqld.log
max_connections=10000
max_connect_errors=10000
# 服务端使用的字符集默认为UTF8
character-set-server=utf8
log_bin_trust_function_creators=1


# ssl配置
ssl_ca=/data/mysql/ssl/ca.pem
ssl_cert=/data/mysql/ssl/server-A-cert.pem
ssl_key=/data/mysql/ssl/server-key.pem
ssl_capath=/data/mysql/ssl

[mysql]
# 设置mysql客户端默认字符集
default-character-set=utf8
max_allowed_packet=200M

[client]
# 设置mysql客户端连接服务端时默认使用的端口
port=3306
default-character-set=utf8
socket=/data/mysql/mysql.sock
```
## 客户端B
```bash
[mysqld]
basedir=/app/module/mysql-8.4.5-linux-glibc2.28-x86_64
datadir=/data/mysql
socket=/data/mysql/mysql.sock
pid-file=/data/mysql/mysqld.pid
server-id=2
log-bin=mysql-bin
relay-log=mysql-relay-bin
auto-increment-increment=2
auto-increment-offset=2
binlog_format=ROW
gtid_mode=ON
enforce_gtid_consistency=ON
lower_case_table_names=1
port=3306
log-error=/data/mysql/log/mysqld.log
max_connections=10000
max_connect_errors=10000
# 服务端使用的字符集默认为UTF8
character-set-server=utf8
log_bin_trust_function_creators=1

# ssl配置
ssl_ca=/data/mysql/ssl/ca.pem
ssl_cert=/data/mysql/ssl/server-B-cert.pem
ssl_key=/data/mysql/ssl/server-key.pem
ssl_capath=/data/mysql/ssl

[mysql]
# 设置mysql客户端默认字符集
default-character-set=utf8
max_allowed_packet=200M

[client]
# 设置mysql客户端连接服务端时默认使用的端口
port=3306
default-character-set=utf8
socket=/data/mysql/mysql.sock
```
# mysql启动
## 启动管理
将mysql归到systemd管理
`vim /etc/systemd/system/mysqld.service `
```bash
[Unit]
Description=MySQL 8.4 Community Server
After=network.target
[Service]
Type=simple
User=mysql
Group=mysql
# 安装目录
ExecStart=/app/module/mysql-8.4.5-linux-glibc2.28-x86_64/bin/mysqld --defaults-file=/etc/my.cnf --pid-file=/data/mysql/mysqld.pid
# 指定 PID 文件（和 my.cnf 保持一致）
PIDFile=/data/mysql/mysqld.pid
# 失败重启等可选项
Restart=on-failure
LimitNOFILE=65535
RestartSec=5s
[Install]
WantedBy=multi-user.target
```
添加后重载systemd
```bash
systemctl daemon-reload
```

## 初始化mysql
如果使用mysql用户启动的话:
```bash
./bin/mysqld --initialize --user=mysql
```
## 启动mysql
```bash
systemctl start mysqld
```
用输出的临时密码修改数据库root用户的密码
```bash
mysql -uroot -p"临时密码"

> ALTER USER 'root'@'localhost' IDENTIFIED BY '密码';
> FLUSH PRIVILEGES;
```
# 配置双主
> 注意证书文件不同于`/etc/my.cnf`中配置的证书文件
> 
## 为两台mysql数据库添加同步用户
```bash
CREATE USER 'repl'@'%' IDENTIFIED BY 'repl用户密码' REQUIRE X509;
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
```
## 配置同步
### 客户端A
```bash
CHANGE REPLICATION SOURCE TO
    SOURCE_HOST='客户端B的ip',
    SOURCE_USER='repl',
    SOURCE_PASSWORD='repl用户密码',
    SOURCE_SSL=1,
    SOURCE_SSL_CA='/data/mysql/ssl/ca.pem',
    SOURCE_SSL_CERT='/data/mysql/ssl/repl-A-cert.pem',
    SOURCE_SSL_KEY='/data/mysql/ssl/repl-key.pem',
    SOURCE_SSL_VERIFY_SERVER_CERT = 0,
    SOURCE_AUTO_POSITION=1;
```
### 客户端B
```bash
CHANGE REPLICATION SOURCE TO
    SOURCE_HOST='客户端A的ip',
    SOURCE_USER='repl',
    SOURCE_PASSWORD='repl用户密码',
    SOURCE_SSL=1,
    SOURCE_SSL_CA='/data/mysql/ssl/ca.pem',
    SOURCE_SSL_CERT='/data/mysql/ssl/repl-B-cert.pem',
    SOURCE_SSL_KEY='/data/mysql/ssl/repl-key.pem',
    SOURCE_SSL_VERIFY_SERVER_CERT = 0,
    SOURCE_AUTO_POSITION=1;
```

## 拉起同步并解决报错
```bash
START REPLICA;
```
**此时会有报错**:
### 客户端A的SQL线程报错
```bash
SHOW REPLICA STATUS\G;
           Replica_IO_Running: Yes
          Replica_SQL_Running: No
                   Last_Error: Coordinator stopped because there were error(s) in the worker(s). The most recent failure being: Worker 1 failed executing transaction '6dbaa9fa-5260-11f0-a223-fa163e768236:3' at source log mysql-bin.000002, end_log_pos 986. See error log and/or performance_schema.replication_applier_status_by_worker table for more details about this failure or others, if any.
```

此时需要跳过该错误
```bash
STOP REPLICA;
SET GTID_NEXT='6dbaa9fa-5260-11f0-a223-fa163e768236:3';
BEGIN; COMMIT;
SET GTID_NEXT='AUTOMATIC';
```

### 客户端B的SQL线程报错
```bash
SHOW REPLICA STATUS\G;
           Replica_IO_Running: Yes
          Replica_SQL_Running: No
                   Last_Error: Coordinator stopped because there were error(s) in the worker(s). The most recent failure being: Worker 1 failed executing transaction '445d23ae-5260-11f0-bec3-fa163e768241:3' at source log mysql-bin.000002, end_log_pos 985. See error log and/or performance_schema.replication_applier_status_by_worker table for more details about this failure or others, if any.
```

此时需要跳过该错误
```bash
STOP REPLICA;
SET GTID_NEXT='445d23ae-5260-11f0-bec3-fa163e768241:3';
BEGIN; COMMIT;
SET GTID_NEXT='AUTOMATIC';
```

## 重新拉起同步
```bash
START REPLICA;
```
此时就能同步了, 后边**安装配置`keepalived`**即可

