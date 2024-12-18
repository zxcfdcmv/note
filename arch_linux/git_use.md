# 使用git提交笔记到github
1. 新建目录，作为本地仓库
  ```shell
  mkdir -p ~/Documents/git/note/
  `
2. 初始化本地仓库
  ```shell
  cd ~/Documents/git/note/
  git init
  `
3. 添加笔记，提交测试
  ```shell
  touch test.md
  git commit -m "test"
  `
4. 连接远程仓库
  ```shell
  git remote set-url origin git@github.com:zxcfdcmv/note.git
  `
5. 可在github设置ssh-key免密登陆
  ```shell
  paru -S openssh
  ssh-keygen
  cat ~/.ssh/id_ed25519.pub
  `
  添加该ssh公钥到github中
6. 推送笔记到github
  ```shell
  git push origin master
  `
