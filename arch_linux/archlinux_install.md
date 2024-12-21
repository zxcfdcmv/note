# 安装前的准备
## 下载ISO映像并准备安装介质
## 启动到live环境
1. 联想重启电脑快速连按`F2`键，注意如果时笔记本的话需要是自带的键盘
2. 出现界面时选择带有`grub2`字样的选项并回车
3. 选择`arch linux install medium`并按回车进入安装环境
4. 此时会以root用户进入一个`虚拟控制台`，默认的shell是bash
## 验证引导模式
验证系统当前的引导模式
```shell
  cat /sys/firmware/efi/fw_plateform_size
```
当结果为64,则系统是以UEFI模式引导且使用64位 x64 UEFI
## 连接到互联网
- 确保系统已经列出并启用了网络接口
  ```shell
    ip link
  ```
- 连接到网络
  1. 有线以太网——连接网线。
  2. WiFi——使用 iwctl 认证无线网络。
  3. 移动宽带调制解调器（移动网卡）--  使用 mmcli 连接到移动网络。
### 使用wifi连接网络
#### 进入交互式提示符
```shell
  iwctl
```
列出所有可用的命令
```
  [iwd]# help
```
#### 连接网络
1. 列出所有wifi设备
```
  [iwd]# device list
```
2. 如果设备或其相应的适配器已关闭，请将其打开
```
  [iwd]# adapter adapter set-property Powered on
  [iwd]# device name set-property Powered on
```
3. 扫描网络，并列出所有可用的网络
```
  [iwd]# station name scan
  [iwd]# station name get-networks
```
4. 连接到一个网络
```
  [iwd]# station name connect SSID
```

#### 检查网络连接
```
  ping archlinux.org
```
## 更新系统时间
```
  timedatectl
```
## 创建硬盘分区、格式化并挂载
# 开始安装系统
# 配置系统
# 重启计算机
