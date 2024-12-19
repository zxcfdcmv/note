# 安装alacritty
```shell
paru -S alacritty
```
# 配置sway默认使用alacritty终端
```shell
# helix ~/.config/sway/config
set $term alacritty
```

# 配置alacritty配置文件
```shell
helix ~/.config/alacritty/alacritty.toml
```
配置文件`alacritty.toml`内容:
```toml
[general]
live_config_reload = true

[colors]
draw_bold_text_with_bright_colors = true

[[colors.indexed_colors]]
color = "#F8BD96"
index = 16

[[colors.indexed_colors]]
color = "#F5E0DC"
index = 17

[colors.bright]
black = "#988BA2"
blue = "#96CDFB"
cyan = "#89DCEB"
green = "#ABE9B3"
magenta = "#F5C2E7"
red = "#F28FAD"
white = "#D9E0EE"
yellow = "#FAE3B0"

[colors.cursor]
cursor = "#F5E0DC"
text = "#1E1D2F"

[colors.normal]
black = "#6E6C7E"
blue = "#96CDFB"
cyan = "#89DCEB"
green = "#ABE9B3"
magenta = "#F5C2E7"
red = "#F28FAD"
white = "#D9E0EE"
yellow = "#FAE3B0"

[colors.primary]
background = "#1E1D2F"
foreground = "#D9E0EE"

[cursor.style]
blinking = "off"
shape = "Block"

[font]
size = 16.0

[font.bold]
family = "JetBrainsMonoNerdFont"
style = "Bold"

[font.bold_italic]
family = "JetBrainsMonoNerdFont"
style = "Bold Italic"

[font.italic]
family = "JetBrainsMonoNerdFont"
style = "Italic"

[font.normal]
family = "JetBrainsMonoNerdFont"
style = "Regular"

[[hints.enabled]]
command = "xdg-open"
post_processing = true
regex = "(magnet:|mailto:|gemini:|gopher:|https:|http:|news:|file:|git:|ssh:|ftp:)[^\u0000-\u001F\u007F-<>\"\\s{-}\\^⟨⟩`]+"

[hints.enabled.mouse]
enabled = true
mods = "Control"

[[keyboard.bindings]]
action = "Paste"
key = "V"
mods = "Control|Shift"

[[keyboard.bindings]]
action = "Copy"
key = "C"
mods = "Control|Shift"

[[keyboard.bindings]]
action = "Paste"
key = "Insert"
mods = "Shift"

[[keyboard.bindings]]
action = "Copy"
key = "Insert"
mods = "Control"

[mouse]
hide_when_typing = true

[[mouse.bindings]]
action = "Copy"
mouse = "Right"

[[mouse.bindings]]
action = "Paste"
mods = "Control"
mouse = "Right"

[[mouse.bindings]]
action = "Paste"
mouse = "Middle"

[[mouse.bindings]]
action = "PasteSelection"
mods = "Control"
mouse = "Middle"

[scrolling]
history = 1000
multiplier = 3

[selection]
save_to_clipboard = true
semantic_escape_chars = ",│`|:\"' ()[]{}<>\t@="

[window]
decorations = "full"
dynamic_title = true
opacity = 0.75
startup_mode = "Fullscreen"
title = "Alacritty"
```
