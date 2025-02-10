# 设置代理
>常用镜像源
- 清华大学：https://pypi.tuna.tsinghua.edu.cn/simple
- 阿里云：https://mirrors.aliyun.com/pypi/simple
- 豆瓣：https://pypi.douban.com/simple
- 华为云：https://repo.huaweicloud.com/repository/pypi/simple

## 临时使用镜像源
在安装命令中添加 `-i` 参数，指定镜像源地址
```shell
pip install pyright -i https://pypi.tuna.tsinghua.edu.cn/simple
```
## 永久设置镜像源
1. 在用户目录下创建或修改`pip`配置文件:
    - Windows: `%APPDATA%\pip\pip.ini`
    - Linux: `~/.pip/pip.conf`
1. 添加如下内容
    ```ini
    [global]
    index-url = https://pypi.tuna.tsinghua.edu.cn/simple
    trusted-host = pypi.tuna.tsinghua.edu.cn
    ```

# 使用
安装模块
```shell
pip install pyright
```
卸载模块
```shell
pip uninstall pyright
```
- 强制卸载
    ```shell
    pip uninstall pyright --force
    ```

# 优化
配置文件中参数优化
```ini
[global]
index-url = https://pypi.tuna.tsinghua.edu.cn/simple
trusted-host = pypi.tuna.tsinghua.edu.cn
# 并行下载
install = --use-deprecated=parallel-install
# 优先使用二进制包可以加快安装速度，避免从源码编译
only-binary : all:
# 启用压缩传输可以减少下载的数据量
compression = true
# 设置超时时间和重试次数
timeout = 100  # 超时时间（秒）
retries = 3     # 重试次数
# 禁用安装确认提示
yes = true
