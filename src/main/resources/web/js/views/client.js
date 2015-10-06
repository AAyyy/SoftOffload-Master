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

window.ClientListView = Backbone.View.extend({

    initialize:function () {
        this.template = _.template(tpl.get('client-list'));
        this.model.bind("change", this.render, this);
        this.model.bind("add", this.render, this);
    },

    render:function (eventName) {
        $(this.el).html(this.template({nclients:this.model.length}));
        _.each(this.model.models, function (c) {
            $(this.el).find('table.client-table > tbody')
                .append(new ClientListItemView({model:c}).render().el);
        }, this);
        return this;
    },

});

window.ClientListItemView = Backbone.View.extend({
    
    tagName:"tr",

    initialize:function () {
        this.template = _.template(tpl.get('client-list-item'));
        this.model.bind("change", this.render, this);
    },

    render:function (eventName) {
        // console.log(this.model.toJSON());
        $(this.el).html(this.template(this.model.toJSON()));
        return this;
    }

});
