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

window.AgentListView = Backbone.View.extend({

    initialize:function () {
        this.template = _.template(tpl.get('agent-list'));
        this.model.bind("change", this.render, this);
        this.model.bind("remove", this.render, this);
    },

    render:function (eventName) {
        $(this.el).html(this.template({nagents:agentl.length}));
        _.each(this.model.models, function (agent) {
            $(this.el).find('table.agent-table > tbody')
                .append(new AgentListItemView({model:agent}).render().el);
        }, this);
        return this;
    },

});

window.AgentListItemView = Backbone.View.extend({
    
    tagName:"tr",

    initialize:function () {
        this.template = _.template(tpl.get('agent-list-item'));
        this.model.bind("change", this.render, this);
    },

    render:function (eventName) {
        $(this.el).html(this.template(this.model.toJSON()));
        
        var clients = this.model.get('client');
        // console.log(clients);
        var clientStr = '[ ';
        if (clients instanceof Array) {
            // console.log("here");
            _.each(clients, function (client) {
                if (clientStr == '[ ') {
                    clientStr += client;
                } else {
                    clientStr += ',<br>' + client;
                }
            });
        }
        clientStr += ' ]';

        $(this.el).append('<td class="vertical-align">' + clientStr + '</td>');

        return this;
    }

});
