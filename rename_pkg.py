import os
import glob

# 1. Update text files
files_to_update = glob.glob(r'app/src/main/java/com/michael/tgnotifier/*.kt')
files_to_update.append('app/build.gradle.kts')

for filepath in files_to_update:
    filepath = os.path.normpath(filepath)
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
        
    content = content.replace('com.michael.tgnotifier', 'com.guberdev.tgnotifier')
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

# 2. Rename directory
try:
    os.rename(r'app/src/main/java/com/michael', r'app/src/main/java/com/guberdev')
except Exception as e:
    print(f"Error renaming directory: {e}")

print("Package renamed successfully")
