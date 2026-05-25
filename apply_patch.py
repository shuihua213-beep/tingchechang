import os
import glob
import re

def process_yaml(content):
    # 移除被抽取到 bootstrap.yml 的重复配置块
    content = re.sub(r'\n  datasource:\n(?: {4}.*(?:\n|$))*', '\n', content)
    content = re.sub(r'\n  redis:\n(?: {4}.*(?:\n|$))*', '\n', content)
    content = re.sub(r'\n  data:\n(?: {4}.*(?:\n|$))*', '\n', content)
    content = re.sub(r'\nauth:\n(?: {2}.*(?:\n|$))*', '\n', content)
    content = re.sub(r'\n  registry:\n(?: {4}.*(?:\n|$))*', '\n', content)
    content = re.sub(r'\n  fastdfs:\n(?: {4}.*(?:\n|$))*', '\n', content)
    
    # 清理空的父级块
    content = re.sub(r'\ncf:\s*\n(?=[a-zA-Z#])', '\n', content)
    content = re.sub(r'\ndubbo:\s*\n(?=[a-zA-Z#])', '\n', content)
    
    # 修复 cf-hk 和 cf-dh 中硬编码的 IP 地址
    content = re.sub(r'baseUrl: http://192\.168\.3\.19', r'baseUrl: ${HK_FORWARD_BASE_URL:http://192.168.3.19}', content)
    content = re.sub(r'localIP: 192\.168\.3\.252', r'localIP: ${HK_FORWARD_LOCAL_IP:192.168.3.252}', content)
    content = re.sub(r'deviceIP: 192\.168\.3\.249', r'deviceIP: ${HK_FORWARD_DEVICE_IP:192.168.3.249}', content)
    content = re.sub(r'port: 8000', r'port: ${HK_FORWARD_PORT:8000}', content)
    
    content = re.sub(r'ip: 192\.168\.3\.200', r'ip: ${DH_CAMERA_IP:192.168.3.200}', content)
    content = re.sub(r'server: http://192\.168\.3\.19:8089', r'server: ${DH_CAMERA_SERVER:http://192.168.3.19:8089}', content)
    content = re.sub(r'ip: 192\.168\.3\.250', r'ip: ${DH_LED_IP:192.168.3.250}', content)
    
    # 清理多余空行
    content = re.sub(r'\n{3,}', '\n\n', content)
    
    return content

files = glob.glob('/app/tingchechang/cf-framework-parent/**/*.yml', recursive=True)
for f in files:
    if 'bootstrap.yml' in f:
        continue
    with open(f, 'r', encoding='utf-8') as file:
        content = file.read()
    
    new_content = process_yaml(content)
    
    if new_content != content:
        with open(f, 'w', encoding='utf-8') as file:
            file.write(new_content)
        print(f"Updated {f}")

print("All application.yml files updated successfully.")
