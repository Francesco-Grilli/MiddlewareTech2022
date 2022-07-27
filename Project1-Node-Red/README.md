# A guide to the usage of the Node-RED section

---

## Index
- [Required packages](#required-packages)
- [Import notes](#import-notes)
- [Starting notes](#starting-notes)

---

### Required packages
The flows in this directory contain nodes that are not available
by default on Node-RED. In order to correctly run the flows, thus, it's
necessary to download and add to the palette some additional packages:
- [node-red-contrib-kafkajs](https://flows.nodered.org/node/node-red-contrib-kafkajs)
- [node-red-dashboard](https://flows.nodered.org/node/node-red-dashboard)
- [node-red-node-ui-table](https://flows.nodered.org/node/node-red-node-ui-table)

We strongly suggest to download the additional packages before trying
to import the flows, otherwise the related nodes might not be recognized
by the application and flows might be imported as incomplete.

---

### Import notes
Upon importing the flows some additional steps are required to let them
run correctly; these steps are mostly related to the adaptation of the
physical paths used in the nodes to the new machine on which the flows 
have to be run.
- In the "bridge" flow, update the path to POI.txt in the "POI-file" node
- In the "avgDump" flow, update the csv paths in the nodes that dump on
them the computed averages and the input messages.
- Update reference to Kafka broker in the Kafka Client node; to access
it, click on any of the Kafka nodes which are present in the flows, then
click on the pencil icon beside client dropdown menu; then change the 
broker's address to match the one you want to refer to.

We strongly advise not to change any topic name, unless strictly necessary:
in order to let the project work correctly, then, it would be necessary
to update also the setting file in the Spark section.

You might want, instead, to change the topic used in the MQTT node; mind
you to update all related references as well in other sections configuration
files.

---

### Starting notes
In order to start the system, simply deploy the nodes on your machine
after having followed the indications in the previous section. On top
of that, you should have already started the kafka broker, as Node-RED
might shut down if the Kafka nodes aren't able to the broker within the
connection timeout.
