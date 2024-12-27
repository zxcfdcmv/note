# AUR加速
1. 安装脚本所用到的工具
```shell
paru -S lftp axel
```
2. 修改`/etc/makepkg.conf`配置文件, paru下载时会读取该文件配置, 将pacman/paru默认下载工具指向自己写的脚本
```shell
#DLAGENTS=('file::/usr/bin/curl -qgC - -o %o %u'
#          'ftp::/usr/bin/curl -qgfC - --ftp-pasv --retry 3 --retry-delay 3 -o %o %u'
#          'http::/usr/bin/curl -qgb "" -fLC - --retry 3 --retry-delay 3 -o %o %u'
#          'https::/usr/bin/curl -qgb "" -fLC - --retry 3 --retry-delay 3 -o %o %u'
#          'rsync::/usr/bin/rsync --no-motd -z %u %o'
#          'scp::/usr/bin/scp -C %u %o')

DLAGENTS=('file::/usr/local/bin/lftp_wrapper.sh %u %o'
          'ftp::/usr/local/bin/lftp_wrapper.sh %u %o'
          'http::/usr/local/bin/lftp_wrapper.sh %u %o'
          'https::/usr/local/bin/lftp_wrapper.sh %u %o'
          'rsync::/usr/bin/rsync --no-motd -z %u %o'
          'scp::/usr/bin/scp -C %u %o')
```

3. 修改`/etc/pacman.con`配置文件, paru下载时也会读取该文件
```shell
XferCommand = /usr/local/bin/lftp_wrapper.sh %u %o
```

4. 在`/usr/local/bin`目录下添加脚本`lftp_wrapper.sh`, 处理URL&调起第三方下载工具下载文件
```shell
#!/usr/bin/env bash

LFTP_BIN=/usr/bin/lftp
AXEL_BIN=/usr/bin/axel

USER_AGENT="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36"
PROCESS_COUNT=$(($(nproc)+1))
URL=$1
OUT_PATH=$2

# check if http or https proxy is set
if [ -n "$http_proxy" ]; then
    PROXY=$http_proxy
elif [ -n "$https_proxy" ]; then
    PROXY=$https_proxy
else
    PROXY=""
fi
if [ -z "$PROXY" ]; then
    # replace "github.com" with "hub.fastgit.xyz" mirror
    URL=${URL//github.com/github.moeyy.xyz/https://github.com}
    # replace "raw.githubusercontent.com" with "raw.fastgit.org" mirror
    # URL=${URL//raw.githubusercontent.com/raw.fastgit.org}
fi


$LFTP_BIN -e \
    "set ssl:verify-certificate false;
    set net:idle 10;
    set net:max-retries 3;
    set net:reconnect-interval-base 3;
    set net:reconnect-interval-max 3;
    set http:user-agent '$USER_AGENT';
    pget -n $PROCESS_COUNT -c $URL -o $OUT_PATH;
    quit;"
# $AXEL_BIN --max-redirect=3 -n $PROCESS_COUNT -a -k -U "$USER_AGENT" $URL -o $OUT_PATH
```

5. 现在使用paru命令即可达到加速效果
