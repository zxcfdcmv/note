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
> 通过编辑`/etc/pacman.conf`来配置pacman

```shell
sudo -e /etc/pacman.conf
```
启用颜色显示和并行下载
```shell
Color
ParallelDownloads = 5
```
启用multilib仓库和archlinuxcn仓库
```shell
[multilib]
Include = /etc/pacman.d/mirrorlist
[archlinuxcn]
Server = https://mirrors.bfsu.edu.cn/archlinuxcn/$arch
```
同步软件数据库
> 如果开启了`archlinuxcn`仓库,要安装`arhclinuxcn-keyring`
```shell
pacman -Syyu
pacman -S archlinuxcn-keyring
```
安装pkgstats
> 定期上传软件列表、镜像源、计算机架构到archlinux官方
```shell
pacman -S pkgstats
```

## 使用pacman
|功能|命令|
|:---|:---|
|安装软件包|`pacman -S 软件包名`|
|移除软件包|`pacman -Rs 软件包名`|
|同步软件数据库并更新系统|`pacman -Syu`|
|强制同步软件数据库并更新系统|`pacman -Syyu`|
|查询软件数据库|`pacman -Ss 字符串`|
|查询文件|`pacman -F 字符串`|

## 软件包缓存定期清理
```shell
pacman -S pacman-contrib
systemctl enable paccache.timer
```

# AUR的使用
## 准备工作
1. 安装`base-devel`软件元包,用来编译软件
```shell
pacman -S base-devel
```
2. 编辑`/etc/makepkg.conf`文件，调整构建软件包的方式，对编译做优化
```shell
sudo -e /etc/makepkg.conf
```
优化项
```shell
CFLAGS="......" # CFLAGS中先将-march和-mtune删除,再添加-march=native
MAKEFLAGS="-j$(nproc)" # 取消该项注释,多核处理器可以启用并行编译,缩短编译时间
BUILDDIR=/tmp/makepkg # 取消该项注释,使用内存文件系统进行编译
COMPRESSZST=(zstd -c -z -q --threads=0 -) # 在压缩时使用多个CPU核心
```
## 安装AUR助手
```shell
pacman -S paru
```
paru的使用方式和pacman一致

# 显卡驱动
## NVIDIA显卡驱动
查看NVIDIA显卡型号
```shell
lspci -k | grep -A -2 -E "(VGA|3D)"
```
然后,在该网站查看显卡型号对应的代号
```
  https://nouveau.freedesktop.org/CodeNames.html
```
|code name|official name|Nvidia 3D object codename|
|:---|:---|:---|
|NV160|GeForce RTX 2060|Turing|
对于Turing系列的显卡,可以安装nvidia-open软件包(适用于linux内核)
```shell
paru -S nvidia-open
```
## 后续工作
1. 编辑`/etc/mkinitcpio.conf`
  ```shell
  $ sudo -e /etc/mkinitcpio.conf

  HOOKS=(... kms ...) # 去掉该配置中的kms
  ```
  重新生成initramfs
  ```shell
  mkinitcpio -P
  ```

2. 编辑`/etc/default/grub`
  ```shell
  $ sudo -e /etc/default/grub

  GRUB_CMDLINE_LINUX_DEFAULT="... nvidia_drm.modeset=1" # 在该项配置中添加该字段, 此处作用为设置DRM为自动启用
  ```
  重新生成grub配置
  ```shell
  grub-mkconfig -o /boot/grub/grub.cfg
  ```
3. 设置pacman钩子,避免更新nvidia驱动后忘记更新initramfs
  ```shell
  /etc/pacman.d/hooks/nvidia.hook

  [Trigger]
  Operation=Install
  Operation=Upgrade
  Operation=Remove
  Type=Package
  Target=nvidia-open
  Target=linux
  # Change the linux part above and in the Exec line if a different kernel is used
  # 如果使用不同的内核，请更改上面的 linux 部分和 Exec 行中的内容，例如更改为Target=nvidia-open
  
  [Action]
  Description=Updating Nvidia module in initcpio
  Depends=mkinitcpio
  When=PostTransaction
  NeedsTargets
  Exec=/bin/sh -c 'while read -r trg; do case $trg in linux) exit 0; esac; done; /usr/bin/mkinitcpio -P'
  ```

4. 重启计算机
  ```shell
  reboot
  ```

# 混成器sway
> sway是一个基于wayland的混成器
> 要使用sway必须先启用DRM(直接渲染管理器)内核级显示模式设置

使用以下命令验证DRM是否自动启用
```shell
cat /sys/module/nvidia_drm/parameters/modeset
```
如果返回Y的话, 证明DRM已经自动启动

## 安装sway
```shell
paru -S sway
```
## 启动sway
1. 安装seatd
  ```shell
  paru -S seatd
  ```
2. 添加用户到seat用户组
  ```shell
  usermod -aG seat $USER
  ```
3. 启动seatd服务
  ```shell
  systemctl enalbe --now seatd
  ```
4. 重新登陆并设置sway自动启动
  在`~/.bashrc`中添加以下内容
  ```shell
  # helix ~/.bashrc

  if [ -z $DISPLAY ] && [ "$(tty)" = "/dev/tty1" ]; then
    exec sway
  fi
  ```

## 配置sway
编辑`~/.config/sway/config`文件来配置sway,在配置文件中添加以下配置
安装clipman剪切板管理器
```shell
paru -S clipman
```
配置
```shell
# 设置字体
font "JetBrainsMonoNerdFont 16"

# 开启xwayland
xwayland enable

# 边框设置为1
default_border pixel

# 使用clipman剪切板
exec wl-paste -t text --watch clipman store --no-persist
```
# 用户目录
用户目录, 如下载、文档等文件夹
```shell
paru -S xdg-user-dirs
```
创建默认目录
```shell
xdg-user-dirs-update
```
# 电源管理
安装电源管理程序tlp
```shell
paru -S tlp
```
激活并启动tlp服务
```shell
systemctl enable --now tlp
```
禁用`systemd-rfkill.service`和`systemd-rfkill.socket`,以避免冲突
```shell
systemctl mask systemd-rfkill.service
systemctl mask systemd-rfkill.socket
```
