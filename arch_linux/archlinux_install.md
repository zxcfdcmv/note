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
```shell
  ping archlinux.org
```
## 更新系统时间
```shell
  timedatectl
```
## 创建硬盘分区、格式化并挂载
### 创建磁盘分区
查看磁盘块设备
```shell
  lsblk
```
进行分区
分区方案:
|挂载点|分区                     |分区类型               |建议大小    |
|:-----|:------------------------|:----------------------|:-----------|
|/boot |/dev/efi_system_partition|EFI系统分区            |500M        |
|SWAP  |/dev/swap_partition      |Linux swap(交换空间)   |至少4GiB    |
|/     |/dev/root_partition      |Linux x86-64 根目录(/) |设备剩余空间|

使用`fdisk`命令分区
```shell
fdisk /dev/nvme0n1
```
### 格式化分区
EFI系统分区(/boot)
```shell
  mkfs.fat -F 32 /dev/efi_system_partition
```
交换分区(swap):
```shell
  mkswap /dev/swap_partition
```
根分区(/):
```shell
  mkfs.ext4 /dev/root_partition
```
### 挂载分区
> 挂载分区一定先挂载根(/)分区，再挂载引导(boot)分区,最后挂载其他分区

根磁盘卷挂载到`/mnt`
```shell
  mount /dev/root_partition  /mnt
```
挂载EFI系统分区
```shell
  mount --mkdir /dev/efi_system_partition /mnt/boot
```
启用交换空间
```shell
  swapon /dev/swap_partition
```
# 开始安装系统
## 选择镜像站
> 类似centos的yum,archlinux的pacman仓库在`/etc/pacman.d/mirrorlist`中

获取位于中国大陆的HTTPS镜像站
```shell
curl -L 'https://archlinux.org/mirrorlist/?country=CN&protocol=https' -o /etc/pacman.d/mirrorlist
```
然后进入该文件将要用到的镜像地址取消注释
## 安装必需的软件包
> 由于现在是在位于启动介质(U盘)ISO映像的`虚拟控制台`中,所以需要使用`pacstrap`脚本,去安装必需的软件包

### 安装基础包组,linux内核和驱动程序
```shell
pacstrap -K /mnt base linux linux-firmware helix
```
### 安装CPU微码包
查看CPU型号
```shell
cat /proc/cpuinfo | grep "model name"
```
- intel的CPU
  ```shell
  pacstrap -K intel-ucode
  ```
- amd的CPU
  ```shell
  pacstrap -K amd-ucode
  ```
### 安装引导加载程序
```shell
pacstrap -K /mnt grub efibootmgr
```
### 安装网络管理器
```shell
pacstrap -K /mnt networkmanager
```
# 配置系统
## 生成fstab文件
> 将挂载信息写入到`/etc/fstab`中,使该挂载永久生效
```shell
genfstab -U /mnt >> /mnt/etc/fstab
```
查看当前磁盘挂载
```shell
# lsblk
NAME        MAJ:MIN RM   SIZE RO TYPE MOUNTPOINTS
nvme0n1     259:0    0 476.9G  0 disk
├─nvme0n1p1 259:1    0   500M  0 part /boot
├─nvme0n1p2 259:2    0     8G  0 part [SWAP]
└─nvme0n1p3 259:3    0 468.5G  0 part /
```
查看生成的/mnt/etc/fstab是否正确
```shell
# cat /mnt/etc/fstab
# /dev/nvme0n1p3
UUID=28ff8108-f1a6-404a-80a6-b5f429e63f9e	/         	ext4      	rw,relatime	0 1

# /dev/nvme0n1p1
UUID=9644-CBE9      	/boot     	vfat      	rw,relatime,fmask=0022,dmask=0022,codepage=437,iocharset=ascii,shortname=mixed,utf8,errors=remount-ro	0 2

# /dev/nvme0n1p2
UUID=e6ae4c6f-7207-4897-a8e0-f2a896dda66c	none      	swap      	defaults  	0 0
```
## chroot到新安装的系统
```shell
arch-chroot /mnt
```
## 设置时区
```shell
ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
```
运行`hwclock`以生成`/etc/adjtime`
```shell
hwclock --systohc
```
## 区域和本地化设置
> 取消注释`zh_CN.UTF-8`,并在系统中安装中文字体后系统中就能显示中文字体

编辑`/etc/locale.gen`,取消注释`en_GB.UTF-8 UTF-8`和`zh_CN.UTF-8 UTF-8`
```shell
# helix /etc/locale.gen
en_GB.UTF-8 UTF-8
zh_CN.UTF-8 UTF-8
```
执行`locale-gen`以生成`locale`信息
```shell
locale-gen
```
创建locale.conf文件,设定LANG变量
> 不要在`locale.conf`文件中设定LANG变量为`zh_CN.UTF-8`,会导致tty上中文显示为方块
```shell
# helix /etc/locale.conf
LANG=en_GB.UTF-8
```
- 设置`en_GB.UTF-8`而不是`en_US.UTF-8`的优点:
  - 进入桌面环境后以 24 小时制显示时间；
  - LibreOffice 等办公软件的纸张尺寸会默认为 A4 而非 Letter(US)；
  - 可尽量避免不必要且可能造成处理麻烦的英制单位。
## 网络配置
创建hostname文件,设置主机名:
```shell
# helix /etc/hostname
local
```
为上边安装的网络管理器设置开机自启
```shell
systemctl enable NetworkManager
```
> 注意,此时还没有连接互联网
## 设置root密码
为root用户设置密码
```shell
echo "想要的密码" | passwd --stdin root
```
## 配置引导加载程序
安装GRUB到计算机
```shell
grub-install --target=x84_64-efi --efi-directory=/boot --bootloader-id=GRUB
```
- 由于esp分区挂载到了`/boot`中,所以`--efi-directory`设置为了`/boot`


生成GRUB配置
```shell
grub-mkconfig -o /boot/grub/grub.cfg
```
# 重启计算机
1. 退出`chroot`环境
  ```shell
  exit
  ```
2. 取消挂载`/mnt`
  ```shell
  umount -R /mnt
  ```
3. 重启计算机
  ```shell
  reboot
  ```
