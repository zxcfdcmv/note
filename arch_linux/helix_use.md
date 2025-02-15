# 安装helix
```shell
  paru -S helix
```

# 配置
## 通用配置
> 配置文件在`~/.config/helix/config.toml`中,
> 也能通过打开helix后，输入`:config-open`来打开配置文件
```toml
theme = "onedark"

[editor]
# Enable soft wrapping (automatic line wrapping)
soft-wrap = { enable = true }
```
- 配置说明
  - `theme`: 主题
  - `soft-wrap`: 
    - `enable`: 启用软换行
    - `max-wrap`: 设置最大换行数,0表示没有限制
    - `max-indent-retain`: 设置最大保留缩进,0表示不保留缩进
    - `wrap-indicator`: 设置换行指示符
    - `wrap-at-text-width`: 设置是否在文本宽度处换行

## language配置
安装模块
```shell
pip install ruff
pip install jedi-language-server
```
在配置文件目录中新建`languages.toml`, 新增python的lsp配置
```toml
use-grammars = { except = [ "wren", "gemini" ] }

[language-server]
ruff = { command = "ruff", args = ["server"] }
jedi = { command = "jedi-language-server" }

[[language]]
name = "python"
scope = "source.python"
injection-regex = "py(thon)?"
file-types = ["py", "pyi", "py3", "pyw", "ptl", "rpy", "cpy", "ipy", "pyt", { glob = ".python_history" }, { glob = ".pythonstartup" }, { glob = ".pythonrc" }, { glob = "SConstruct" }, { glob = "SConscript" }]
shebangs = ["python", "uv"]
roots = ["pyproject.toml", "setup.py", "poetry.lock", "pyrightconfig.json"]
comment-token = "#"
language-servers = ["ruff", "jedi"]
formatter = { command = "ruff", args = ["format", "--stdin"] }
# TODO: pyls needs utf-8 offsets
indent = { tab-width = 4, unit = "    " }

[[grammar]]
name = "python"
source = { git = "https://github.com/tree-sitter/tree-sitter-python", rev = "4bfdd9033a2225cc95032ce77066b7aeca9e2efc" }

[language.debugger]
name = "debugpy"
transport = "stdio"
command = "debugpy"

[[language.debugger.templates]]
name = "binary"
request = "launch"
completion = [ { name = "binary", completion = "filename" } ]
args = { program = "{0}" }

[[language.debugger.templates]]
name = "binary (terminal)"
request = "launch"
completion = [ { name = "binary", completion = "filename" } ]
args = { program = "{0}", runInTerminal = true }

[[language.debugger.templates]]
name = "attach"
request = "attach"
completion = [ "pid" ]
args = { pid = "{0}" }
```

# 使用
## 移动(g)
`h`: 向左移动
`l`: 向右移动
`j`: 向下移动
`k`: 向上移动
`gg`: 移动到文件顶部
`ge`: 移动到文件底部
`gh`: 移动到行开头
`gl`: 移动到行结尾
## 选择(x/w/e/b)
`w`: 选择到下个单词
`e`: 选择到当前单词的结尾
`b`: 向后选择到当前单词的开头
`v`: 进入选择模式
`x`: 选择当前整行, 再次输入`x`选择下一行
`;`: 收缩选择, 取消选择而不移动光标, 
`alt + ;`: 翻转选择, 将光标移动到选择的第一个字符
`shift + c`: 多光标模式
`,`: 退出多光标模式
## 编辑(i/r/d/u/y/p)
`i`: 插入
`shift + i`: 在行首插入
`shift + a`: 在行尾插入
`o`: 在光标下方打开新行并插入
`shift + o`: 在光标上方打开新行并插入
`r`: 替换
`c`: 删除当前字符并插入
`d`: 删除选中的文本
`alt + d`: 删除选中的文本, 但不复制该文本到寄存器
`u`: 撤销
`shift + u`: 反撤销
`s`: 选择 选择中的匹配项
`复制到寄存器`: 选中文本后`d`或者`y`
`复制到系统剪切板`: 选中文本后`Space + y`
`打开新的编辑`: `ctrl + w`后, `n + v`
## 搜索
`/`: 向前搜索
`?`: 向后搜索
`n`: 跳到下一个搜索匹配项
`N`: 跳到上一个搜索匹配项

## 其他
`>`: 缩进
`<`: 取消缩进
`Ctrl + s`: 手动保存当前位置到跳转列表
`Ctrl + i`: 在跳转列表中前进
`Ctrl + o`: 在跳转列表中后退
`~`: 更改所选字母的大小写
\`\`: 将所选字母设为小写
alt + \`: 将所选字母设为大写
`Ctrl + c`: 注释一行
