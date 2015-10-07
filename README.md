# SoftOffload Master

SoftOffload Master is an extended Floodlight controller for mobile traffic offloading. It is implemented based on Floodlight v0.91, and cooperates with the [SoftOffload Agent](https://github.com/TKTL-SDN/SoftOffload-Agent) and the [client applicaion](https://github.com/TKTL-SDN/SoftOffload-Client) together to provide traffic offloading services in an SDN approach.

We develop our [offloading master](https://github.com/TKTL-SDN/SoftOffload-Master/tree/eit-sdn/src/main/java/net/floodlightcontroller/mobilesdn) on Floodlight as an extension application. To facilitate system building, the rest of Floodlight code is also included in this repo.

Our current SoftOffload master does **not** support Floodlight v1.0+, which switches to OpenFlowJ-Loxigen for implementing the OpenFlow protocol.

## Usage

### Download the code

    $ git clone https://github.com/TKTL-SDN/SoftOffload-Master.git

### Building and Running

The master shall be run on a central server that has IP reachability to all APs and OF switches in the system. Before running your master, please first check the configuration files in the "[resources](https://github.com/TKTL-SDN/SoftOffload-Master/blob/eit-sdn/src/main/resources)" folder, and make sure the settings are matched with your network.

Our offloading module is called `net.floodlightcontroller.mobilesdn`, and it has been already included in `floodlight.modules` in the example Floodlight system configuration [floodlightdefault.properties](https://github.com/TKTL-SDN/SoftOffload-Master/blob/eit-sdn/src/main/resources/floodlightdefault.properties). We explain our key parameters in this configuration file as follows:

* `mobilesdn.Master.masterPort`: this is the UDP communication port to SoftOffload agent. It shall be the same as the one used in the [agent configuration file](https://github.com/TKTL-SDN/SoftOffload-Agent/tree/eit-sdn/conf/local-agent).

* `mobilesdn.Master.ofMonitorInterval`: this parameter is used to adjust the traffic monitoring interval on OpenFlow switches. 2 means the interval is 2 seconds.

* `mobilesdn.Master.ofMonitorMaxNum`: how many monitoring turns are required for triggering offloading.

* `mobilesdn.Master.apConfig`: this shall point to an apConfig file, which is required for traffic offloading. An example apConfig file is given in the `src/main/resources/ap.properties`. `ManagedIP` is the reachable IP address of the local agent running on the AP, `AUTH` is the authentication method and corresponding password (like "open", "wpa|your_password"). OFPort is port which this AP connects to the OF switch. `DownlinkBW` is the downstream bandwidth in Mbps.

    ```
    # AP1
    ManagedIP 192.168.3.30
    SSID sdntest
    BSSID 9c:d3:6d:10:a9:b8
    AUTH wpa|testeitsdn
    OFPort 2
    DownlinkBW 16

    # AP2 only for test
    ManagedIP 192.168.1.21
    SSID sdntest1
    BSSID 90:94:e4:07:ad:0f
    AUTH wpa|testeitsdn
    OFPort 3
    DownlinkBW 80
    ```

* `mobilesdn.Master.networkFile`: this shall point to a network topology file. An example file is given in the `src/main/resources/networks.properties` and shown below. In our system, a network slice is defined by the OFswitch outport. If two APs are using the same access switch outport, they are considered as in the same network slice. `BandWidth` is the total downstream bandwidth for this switch outport in Mbps.

    ```
    # Network-1
    # OFSwitchMAC 00:12:3f:22:45:66
    OFSwitchIP 192.168.10.125
    OutPort 1
    BandWidth 2
    AP 192.168.3.30 192.168.1.21
    ```

* other: `mobilesdn.Master.enableCellular` is not used in our current implementation, you may leave this unchanged.




**To build and run the master**:

```
$: cd SoftOffload-Master
$: ant
$: java -jar floodlight.jar
```

### REST API

To facilitate application development and future demonstration, we also provide a few REST APIs for SoftOffload. The interfaces follow the Floodlight style ([Floodlight native REST API](https://floodlight.atlassian.net/wiki/display/floodlightcontroller/Floodlight+REST+API)).

We also add demonstration components in the Floodlight UI with our REST API. You can go to your browser and type in a URL as follows to check wireless information:

http://localhost:8080/ui/index.html (replace localhost with the proper IP address of your Floodlight controller).

The REST call is like this:

```
curl http://localhost:8080/wm/softofflod/agents/json
```

Current available interfaces via REST:

| URI                               | Method | Description                    |
| --------------------------------- | ------ | ------------------------------ |
| /wm/softoffload/agents/json       | GET    | Retrieve all registered agents |
| /wm/softoffload/agent/(id)/json   | GET    | Retrieve agent info for (id)   |
| /wm/softoffload/client/(id)/json  | GET    | Retrieve client info for (id)  |


## Licence

Under the [Apache 2.0 License](https://www.apache.org/licenses/LICENSE-2.0)
