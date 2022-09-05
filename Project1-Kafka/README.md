# Kafka implementation description

## Index
- [Kafka components](#kafka-components)
- [Topic additional settings](#topic-additional-settings)

## Kakfa components
Kafka is present in the system in the following sections
- In the Node-RED bridge flow there is the producer which is responsible of injecting clean data into the analysis section
- In the Node-RED average-dump flow there are consumers which are meant to retrieve content from topics for debugging purposes
- In the Spark section all the streaming queries are fed from the Kafka topics and write onto other ones

## Topic additional settings
The Last Over Threshold topic requires additional settings to work properly: indeed, it must be log compacted. The compaction configurations that have been used are:
- `cleanup.policy=compact`
- `segment.bytes=4096`
- `min.cleanable.dirty.ratio=0.01`
- `delete.retention.ms=5000`
- `segment.ms=5000`

To set the configurations to the topic[^docker]:
1. Create topic with `docker exec kafka1 kafka-topics --create --topic lastOverThreshold --bootstrap-server localhost:9092`[^server]
2. Add configs, for example `cleanup.policy` to the topic with `docker exec kafka1 kafka-configs --alter --topic lastOverThreshold --add-config cleanup.policy=compact --bootstrap-server localhost:9092`[^server]
3. Repeat step 2 for all the other previously mentioned configs

All other topics don't require additional configuration and can be created automatically upon production of the first messages.

[^docker]: Following instructions refer to docker deployment (see general README)
[^server]: Replace name of the server with yours
