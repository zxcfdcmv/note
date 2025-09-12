import sys
import yaml
import argparse
from pathlib import Path
import fcntl
import time
def load_yaml(file_path):
    """加载yaml文件内容."""
    with open(file_path, 'r') as file:
        return yaml.safe_load(file)
def save_yaml(data, file_path):
    """保存数据到yaml文件."""
    with open(file_path, 'w') as file:
        yaml.dump(data, file, sort_keys=False)
def find_project(data, project_name):
    """在YAML数据中通过名称查找项目."""
    for project in data['projectInfos']:
        if project['name'] == project_name:
            return project
    return None
def find_build_env(project, env_name):
    """在项目中按名称查找构建环境."""
    for env in project['buildEnvs']:
        if env['name'] == env_name:
            return env
    return None
def find_module_in_env(env, module_name):
    """在构建环境中按名称查找模块."""
    for ne in env['nes']:
        if ne['name'] == module_name:
            return ne
    return None
def get_highest_port(env):
    """在一个环境的nes中获取环境中的最大端口号."""
    if not env['nes']:
        return 47070  # Starting port - 1
    return max(ne['port'] for ne in env['nes'])
def add_repository_and_module(data, project_name, repo_name, module_name, ip_address):
    """添加新的代码仓和模块到yaml文件."""
    project = find_project(data, project_name)
    if not project:
        print(f"项目 '{project_name}' 没有发现.")
        return False
    
    # Check if repository exists
    env = find_build_env(project, repo_name)
    if env:
        print(f"覆盖率环境 '{repo_name}' 已经存在.")
        
        # Check if module already exists in this repository
        existing_module = find_module_in_env(env, module_name)
        if existing_module:
            print(f"模块 '{module_name}' 已经存在于覆盖率环境 '{repo_name}' 中, 端口为: {existing_module['port']}")
            return False
    else:
        # Create new environment if repository doesn't exist
        env = {
            'name': repo_name,
            'nes': []
        }
        project['buildEnvs'].append(env)
        print(f"创建新的覆盖率环境: '{repo_name}'")
    
    # Calculate next port (highest + 1)
    highest_port = get_highest_port(env) if env['nes'] else 47070
    new_port = highest_port + 1
    
    # Add new module
    new_module = {
        'ip': ip_address,
        'name': module_name,
        'port': new_port
    }
    env['nes'].append(new_module)
    print(f"添加新的模块: '{module_name}', 端口为: {new_port}")
    
    return True
def safe_update_yaml(file_path, project_name, repo_name, module_name, ip_address):
    """带文件锁的安全更新YAML文件"""
    max_retries = 5
    retry_delay = 0.5
    
    for attempt in range(max_retries):
        try:
            with open(file_path, 'r+') as f:
                # 获取排他锁
                fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
                
                # 读取文件内容
                data = yaml.safe_load(f)
                
                # 执行更新操作
                success = add_repository_and_module(
                    data, 
                    project_name, 
                    repo_name, 
                    module_name, 
                    ip_address
                )
                
                if success:
                    # 写回文件
                    f.seek(0)
                    yaml.dump(data, f, sort_keys=False)
                    f.truncate()
                
                # 释放锁
                fcntl.flock(f, fcntl.LOCK_UN)
                return success
                
        except (IOError, BlockingIOError) as e:
            if attempt == max_retries - 1:
                print(f"无法获取文件锁，已达到最大重试次数: {e}")
                raise
            time.sleep(retry_delay)
        except Exception as e:
            print(f"处理YAML文件时出错: {e}")
            raise
def main():
    parser = argparse.ArgumentParser(description='添加代码仓和模块到yaml文件')
    parser.add_argument('file', help='yaml文件路径')
    parser.add_argument('--project', required=True, help='项目名称(例如: IPTrace)')
    parser.add_argument('--repo', required=True, help='代码仓名称(例如: CMCCLOGQUERY)')
    parser.add_argument('--module', required=True, help='模块名称(例如: httpif)')
    parser.add_argument('--ip', required=True, help='模块的IP地址')
    
    args = parser.parse_args()
    
    # Check if file exists
    file_path = Path(args.file)
    if not file_path.exists():
        print(f"Error: 文件不存在 '{args.file}'")
        sys.exit(1)
    
    try:
        success = safe_update_yaml(
            args.file,
            args.project, 
            args.repo, 
            args.module, 
            args.ip
        )
        
        if success:
            print("配置更新成功")
            sys.exit(0)
        else:
            print("操作已完成，未进行任何更改")
            sys.exit(2)
            
    except Exception as e:
        print(f"处理过程中出错: {e}")
        sys.exit(1)
if __name__ == '__main__':
    main()
