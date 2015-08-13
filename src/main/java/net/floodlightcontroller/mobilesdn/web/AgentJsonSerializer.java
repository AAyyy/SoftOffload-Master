/**
 * 
 */
package net.floodlightcontroller.mobilesdn.web;

import java.io.IOException;

import net.floodlightcontroller.mobilesdn.APAgent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * @author Yanhe Liu <yanhe.liu@cs.helsinki.fi>
 *
 */
public class AgentJsonSerializer extends JsonSerializer<APAgent> {

    /**
     * Handles serialization for APAgent
     */
    @Override
    public void serialize(APAgent agent, JsonGenerator jGen,
            SerializerProvider serializer) throws IOException,
            JsonProcessingException {
        
        jGen.writeStartObject();
        
        jGen.writeArrayFieldStart("ssid");
        jGen.writeString(agent.getSSID());
        jGen.writeEndArray();

        jGen.writeArrayFieldStart("bssid");
        jGen.writeString(agent.getBSSID());
        jGen.writeEndArray();
        
        jGen.writeEndObject();
    }
    
    
    /**
     * Tells SimpleModule that we are the serializer for APAgent
     */
    @Override
    public Class<APAgent> handledType() {
        return APAgent.class;
    }

}
