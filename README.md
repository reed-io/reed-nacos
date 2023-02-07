# REED.IO updated nacos 

原NACOS: [README文件](./README_NACOS.md)


```shell
mvn -Prelease-nacos -Dmaven.test.skip=true -Dpmd.skip=true clean install -U -X -Drat.skip=true
```

ARM Mac打开[pom.xml](./pom.xml)文件,启用properties中的`os.detected.classifier`
```xml
<os.detected.classifier>osx-x86_64</os.detected.classifier>
```
