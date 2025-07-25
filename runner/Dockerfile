FROM eclipse-temurin:21

RUN mkdir /folder/ && chown 1000:1000 /folder/

USER 1000

ENTRYPOINT ["/tgchat_history_saver.runner/bin/tgchat_history_saver.runner", "/config.json"]

ADD ./build/distributions/tgchat_history_saver.runner.tar /
