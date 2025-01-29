FROM bellsoft/liberica-openjdk-alpine:19

USER 1000

ENTRYPOINT ["/tgchat_history_saver.runner/bin/tgchat_history_saver.runner", "/config.json"]

ADD ./build/distributions/tgchat_history_saver.runner.tar /
