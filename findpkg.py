import zipfile

aar_path = r'C:\Users\michael.guber\.gradle\caches\modules-2\files-2.1\com.github.tdlibx\td\1.8.56\97e767ce278c1d01ef5aeb6e957d37f58728efa7\td-1.8.56.aar'
with zipfile.ZipFile(aar_path, 'r') as aar:
    with zipfile.ZipFile(aar.open('classes.jar')) as jar:
        for info in jar.infolist():
            if 'TdApi' in info.filename:
                print(info.filename)
                break
