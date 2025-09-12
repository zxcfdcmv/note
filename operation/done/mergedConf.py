import argparse
import os
import sys
import shutil
from javaproperties import Properties

def merge_properties(old_path, new_path, merged_path):
    """合并两个.properties文件, 保留旧文件中的值."""
    old_props = Properties()
    with open(old_path, 'r', encoding='utf-8') as f:
        old_props.load(f)
    with open(new_path, 'r', encoding='utf-8') as f_in, open(merged_path, 'w', encoding='utf-8') as f_out:
        for line in f_in:
            stripped = line.strip()
            if '=' in stripped and not stripped.startswith(('#', '!')):
                key = stripped.split('=', 1)[0].strip()
                if key in old_props:
                    f_out.write(f"{key}={old_props[key]}\n")
                    continue
            f_out.write(line)

# def merge_yaml(old_file, new_file, output_file):
#     """使用yq命令合并两个YAML文件."""
#     yq_expression = '''
#     select(fileIndex==1) as $old | 
#     select(fileIndex==0) | 
#     . * ($old | del(.ignorePattern, .interfacePattern, .csrf_white_list))
#     '''
#     cmd = ['yq', 'eval-all', yq_expression, new_file, old_file]
#     try:
#         result = subprocess.run(cmd, check=True, capture_output=True, text=True, encoding='utf-8')
#         with open(output_file, 'w', encoding='utf-8') as f:
#             f.write(result.stdout)
#     except subprocess.CalledProcessError as e:
#         sys.stderr.write(f"yq command failed: {e.stderr}\n")
#         sys.exit(1)  

def _merge_node(new_node, old_node, path=(), prefer_new_keys=None):
    """
    递归合并两个yaml节点
    - 默认：旧值优先
    - 如果 key 在 prefer_new_keys 中：新值优先
    - path 是当前键路径，用于匹配 prefer_new_keys（支持顶层名或用'.'的路径）
    """
    if prefer_new_keys is None:
        prefer_new_keys = set()

    def should_prefer_new(path_tuple):
        # 支持两种匹配：顶层键名 或 用'.'连接的完整路径
        dotted = ".".join(map(str, path_tuple))
        return (path_tuple and path_tuple[0] in prefer_new_keys) or (dotted in prefer_new_keys)

    # dict 合并
    if isinstance(new_node, dict) and isinstance(old_node, dict):
        for key in new_node:
            new_val = new_node[key]
            old_has_key = key in old_node
            old_val = old_node.get(key, None)
            sub_path = path + (key,)
            if isinstance(new_val, dict) and isinstance(old_val, dict):
                _merge_node(new_val, old_val, sub_path, prefer_new_keys)
            elif isinstance(new_val, list) and isinstance(old_val, list):
                # 简单策略：默认旧值优先，除非 prefer_new
                if should_prefer_new(sub_path):
                    new_node[key] = new_val
                else:
                    new_node[key] = old_val
            else:
                # 标量或类型不同：按优先策略取值
                if should_prefer_new(sub_path):
                    # 新值优先：只有当新值存在才用新值，否则保留旧值
                    if new_val is not None:
                        new_node[key] = new_val
                    elif old_has_key:
                        new_node[key] = old_val
                else:
                    # 旧值优先：只有当旧值存在才覆盖
                    if old_has_key:
                        new_node[key] = old_val
        return new_node

    # list 或 其他：按优先策略返回（调用方负责）
    return old_node if (old_node is not None and not should_prefer_new(path)) else new_node

def merge_yaml(old_path, new_path, output_path, prefer_new_keys=None):
    from ruamel.yaml import YAML
    yaml = YAML()
    yaml.preserve_quotes = True            # 保留引号
    yaml.allow_unicode = True
    yaml.width = 1000000                   # 极大宽度，避免自动换行
    yaml.reflow_strings = False     # 不重排长字符串

    if prefer_new_keys is None:
        prefer_new_keys = set()

    with open(new_path, 'r', encoding='utf-8') as fnew, open(old_path, 'r', encoding='utf-8') as fold:
        new_data = yaml.load(fnew)
        old_data = yaml.load(fold)
        merged_data = _merge_node(new_data, old_data, (), prefer_new_keys)

    with open(output_path, 'w', encoding='utf-8') as fout:
        yaml.dump(merged_data, fout)

def copy_file(src_path, dst_path):
    """直接将源文件复制到目标路径，确保父目录存在"""
    parent_dir = os.path.dirname(dst_path)
    if not os.path.exists(parent_dir):
        os.makedirs(parent_dir)
    try:
        shutil.copy2(src_path, dst_path)
    except IOError as e:
        sys.stderr.write(f"复制文件失败: {e}\n")
        sys.exit(1)

def process_file(old_path, new_path, output_path, prefer_new_keys=None):
    """处理单个文件"""
    # 处理旧文件不存在的情况
    if not os.path.exists(old_path):
        if not os.path.exists(new_path):
            sys.stderr.write(f"New file not found: {new_path}\n")
            sys.exit(1)
        sys.stderr.write(f"Notice: 旧文件 {old_path} 不存在，已复制新文件到 {output_path}\n")
        copy_file(new_path, output_path)
        return

    # 检查新文件是否存在（当旧文件存在时）
    if not os.path.exists(new_path):
        sys.stderr.write(f"New file not found: {new_path}\n")
        sys.exit(1)

    # 获取文件扩展名
    old_ext = os.path.splitext(old_path)[1].lower()

    # 根据文件类型选择处理方式
    if old_ext in ('.xml', '.txt'):
        # 对于XML和TXT文件，直接复制新文件
        copy_file(new_path, output_path)
    elif old_ext in ('.yaml', '.yml'):
        merge_yaml(old_path, new_path, output_path, prefer_new_keys)
    elif old_ext == '.properties':
        merge_properties(old_path, new_path, output_path)
    else:
        # 对于不支持的文件类型，直接复制旧文件
        sys.stderr.write(f"Notice: 不支持的文件类型 {old_ext}，已复制旧文件到 {output_path}\n")
        copy_file(old_path, output_path)

def process_directory(old_dir, new_dir, output_dir, prefer_new_keys=None):
    """处理目录中的所有文件（忽略子目录）"""
    os.makedirs(output_dir, exist_ok=True)

    # 获取旧目录中的所有文件（不递归遍历子目录）
    old_files = {file for file in os.listdir(old_dir) if os.path.isfile(os.path.join(old_dir, file))}
    # 获取新目录中的所有文件
    new_files = {file for file in os.listdir(new_dir) if os.path.isfile(os.path.join(new_dir, file))}

    # 处理所有文件
    all_files = old_files.union(new_files)
    for file in all_files:
        old_path = os.path.join(old_dir, file) if file in old_files else None
        new_path = os.path.join(new_dir, file) if file in new_files else None
        output_path = os.path.join(output_dir, file)
        
        if old_path and new_path:
            process_file(old_path, new_path, output_path, prefer_new_keys)
        elif new_path:
            copy_file(new_path, output_path)
        elif old_path:
            copy_file(old_path, output_path)

def main():
    parser = argparse.ArgumentParser(description='合并配置文件.')
    parser.add_argument('--old', required=True, help='旧文件或目录的路径')
    parser.add_argument('--new', required=True, help='新配置文件或目录的路径')
    parser.add_argument('--output', required=True, help='配置文件合并后存放的路径')

    args = parser.parse_args()

    # 默认指定需要新值优先的键
    prefer_new_keys = {"ssf.sysmgr.sessionfilter.ignorePattern", "ssf.xssFilter.ignorePattern", "ssf.xssFilter.interfacePattern", "ssf.csrfFilter.csrf_white_list"}

    old_is_dir = os.path.isdir(args.old)
    new_is_dir = os.path.isdir(args.new)

    if old_is_dir or new_is_dir:
        # 如果有一个参数是目录，另一个也必须是目录
        if not (old_is_dir and new_is_dir):
            sys.stderr.write("Error: 如果 --old 或 --new 是目录，另一个也必须是目录\n")
            sys.exit(1)
        # 处理目录
        process_directory(args.old, args.new, args.output, prefer_new_keys)
    else:
        # 处理单个文件
        process_file(args.old, args.new, args.output, prefer_new_keys)

if __name__ == '__main__':
    main()
