import os
import xml.etree.ElementTree as ET
import sys

def find_poms(root_dir):
    poms = []
    for dirpath, dirnames, filenames in os.walk(root_dir):
        for filename in filenames:
            if filename == 'pom.xml':
                poms.append(os.path.join(dirpath, filename))
    return poms

poms = find_poms('/app/tingchechang')
for pom in poms:
    with open(pom, 'r', encoding='utf-8') as f:
        content = f.read()
        if 'jackson' in content.lower():
            print(f"JACKSON in {pom}")
