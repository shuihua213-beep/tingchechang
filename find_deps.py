import os
import xml.etree.ElementTree as ET

def find_poms(root_dir):
    poms = []
    for dirpath, dirnames, filenames in os.walk(root_dir):
        for filename in filenames:
            if filename == 'pom.xml':
                poms.append(os.path.join(dirpath, filename))
    return poms

poms = find_poms('/app/tingchechang')
for pom in poms:
    try:
        tree = ET.parse(pom)
        root = tree.getroot()
        ns = {'mvn': 'http://maven.apache.org/POM/4.0.0'}
        
        # Look for dependencies with version
        for dep in root.findall('.//mvn:dependency', ns):
            group = dep.find('mvn:groupId', ns)
            art = dep.find('mvn:artifactId', ns)
            ver = dep.find('mvn:version', ns)
            
            if group is not None and art is not None and ver is not None:
                g_text = group.text or ''
                a_text = art.text or ''
                v_text = ver.text or ''
                
                if 'spring' in g_text.lower() or 'spring' in a_text.lower() or 'jackson' in g_text.lower() or 'jackson' in a_text.lower() or 'boot' in g_text.lower() or 'boot' in a_text.lower():
                    print(f"[{pom}] {g_text}:{a_text}:{v_text}")
    except Exception as e:
        print(f"Error parsing {pom}: {e}")
