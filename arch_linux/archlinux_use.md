# 登陆并连接互联网
## 使用root用户登陆
## 连接互联网
> 需要前边已经安装networkmanager

列出可用的wifi
```shell
nmcli device wifi list
```
连接wifi
```shell
nmcli device wifi connect "SSID" password "wifi密码"
```
测试是否连接互联网
```shell
ping archlinux.org
```
- `ctrl+c`结束ping命令

# 用户和用户组
## 创建普通用户,并设置密码
```shell
useradd -m -G wheel 用户名
echo "想用的密码" | passwd --stdin 用户名
```
## 权限提升
### 安装权限提升工具
```shell
pacman -S sudo
```
### 配置普通用户可以通过sudo提升权限
```shell
# helix /etc/sudoers
%wheel ALL=(ALL:ALL) NOPASSWD: ALL
```
- 将该行取消注释,可在用sudo提权时不用输入密码
### 配置环境变量,添加下面的配置
>设置系统文件使用sudo去编辑,而不是文本编辑工具
```shell
# helix /etc/environment
SUDO_EDITOR=helix
```
然后`win+shift+e`注销root用户,并使用上边新建的普通用户重进即可生效
此时可使用以下命令对系统文件进行操作
```shell
sudo -e /etc/sudoers
```
# systemd的使用
## 服务管理
|功能|命令|
|:---|:---|
|立即启动单元|`systemctl start 单元`|
|立即停止单元|`systemctl stop 单元`|
|重新启动单元|`systemctl restart 单元`|
|显示单元的状态|`systemctl status 单元`|
|开机自动启动单元|`systemctl enable 单元`|
|开机自动启动单元,并立即启动|`systemctl enable --now 单元`|
|取消开机自动启动单元|`systemctl disable 单元`|
|列出正在运行的单元|`systemctl list-units`|
|列出失败的单元|`systemctl --failed`|

## 日志查看
|功能|命令|
|:---|:---|
|列出失败的单元|`systemctl --failed`|
|列出本次启动后的错误日志|`journalctl -b -p 3`|
|列出本次启动后的全部日志|`journalctl -b`|
### 显示开关机日志
> 用于在开关机出现错误时进行维修

编辑文件
```shell
$ sudo -e /etc/default/grub

# 去掉该行内的"quiet"
GRUB_CMDLINE_LINUX_DEFAULT="loglevel=3 nvidia_drm.modeset=1"
```
应用该变更
```shell
grub-mkconfig -o /boot/grub/grub.cfg
```

# pacman的配置与使用
## 配置pacman
> 通过编辑`/etc/pacman.conf`来
