# 安装helix
`
  paru -S helix
`

# 配置
> 配置文件在`~/.config/helix/config.toml`中
> 也能通过打开helix后，输入`:config-open`来打开配置文件
```toml
theme = "onedark"

[editor]
# Enable soft wrapping (automatic line wrapping)
soft-wrap = { enable = true, max-wrap = 0, max-indent-retain = 0, wrap-indicator = "↩", wrap-at-text-width = true }
```
- 配置说明
  `theme`: 主题
  `soft-wrap`: 
  - `enable`: 启用软换行
  - `max-wrap`: 设置最大换行数,0表示没有限制
  - `max-indent-retain`: 设置最大保留缩进,0表示不保留缩进
  - `wrap-indicator`: 设置换行指示符
  - `wrap-at-text-width`: 设置是否在文本宽度处换行
