FROM registry.cn-beijing.aliyuncs.com/reed_io/centos7-java8:0.1.0

ENV DB_URL localhost:3306
ENV DB_USER root
ENV DB_PWD root
ENV MODE cluste
ENV BASE_DIR /nacos
WORKDIR /nacos

COPY distribution/target/nacos-server-2.2.0/nacos/ $BASE_DIR

EXPOSE 8848
ENTRYPOINT [ "sh", "-c", "ls -al $BASE_DIR && ls -al $BASE_DIR/bin && $BASE_DIR/bin/startup.sh -m standalone && tail -f $BASE_DIR/logs/start.out" ]

