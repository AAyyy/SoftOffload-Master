/*
    Copyright 2012 IBM

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

window.Agent = Backbone.Model.extend({

    urlRoot:"/wm/softoffload/agent/",

    defaults: {
        ssid: '',
        bssid: '',
        managedip: '',
        auth: '',
        downbandwidth: '',
        downrate: '',
        clientnum: 0,
        client: '',
    },

    initialize:function () {
        var self = this;

        // console.log(JSON.stringify(self));
        $.ajax({
            url:hackBase + "/wm/softoffload/agent/" + self.id + "/json",
            dataType:"json",
            success:function (data) {
                // console.log("fetched agent " + self.attributes.ip);
                // console.log(data);
                self.set(data);
                self.set('downrate', (self.get('downrate') / 1000000).toFixed(2));
            }
        });

        // console.log(JSON.stringify(self));
        self.trigger('add');
        this.cltCollection = new ClientCollection();
    },

    fetch:function () {
        this.initialize()
    },

    loadClients:function () {
        var self = this;

        self.cltCollection.reset();
        $.when(
            _.each(self.get('client'), function (cltMac) {
                $.ajax({
                    url:hackBase + "/wm/softoffload/client/" + cltMac + "/json",
                    dataType:"json",
                    success:function (data) {
                        // console.log("client data: " + JSON.stringify(data));
                        self.cltCollection.add({mac: data['mac'],
                                                ip: data['ip'],
                                                downrate: (data['downrate'] / 1000000).toFixed(3)});
                        // console.log(self.cltCollection.toJSON());
                    }
                });
            })
        ).done(
            self.cltCollection.trigger('add')
        );

        
    },

});

window.AgentCollection = Backbone.Collection.extend({

    model:Agent,

    fetch:function () {
        var self = this;
        //console.log("fetching switch list")
        $.ajax({
            url:hackBase + "/wm/softoffload/agents/json",
            dataType:"json",
            success:function (data) {
                // console.log("fetched agent list: " + data.length);
                // console.log(JSON.stringify(data));
                self.reset();

                _.each(data, function(agent) {
                    // old_ids = _.without(old_ids, agent['managedip']);
                    self.add({bssid: agent['bssid'], 
                              ssid: agent['ssid'], 
                              id: agent['managedip']})});

                // console.log(JSON.stringify(self));
            },
        });
    },

});
