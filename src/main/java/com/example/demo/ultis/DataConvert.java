package com.example.demo.ultis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
public class DataConvert {
    protected final static Logger logger = LoggerFactory.getLogger(DataConvert.class);

    public ArrayList<Object> convertJsonToArray(JsonArray jsonArray){
        ArrayList<Object> list = new ArrayList<Object>();
        try {
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement je = jsonArray.get(i);
                if (je.isJsonObject()) {
                    list.add(this.convertJsonToMap(je.getAsJsonObject()));
                } else if (je.isJsonArray()) {
                    list.add(this.convertJsonToArray(je.getAsJsonArray()));
                } else {
                    list.add(je.getAsString());
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return list;
    }

    public Map<String, Object> convertJsonToMap(JsonObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        try {
            Set<Map.Entry<String, JsonElement>> entries = jsonObject.entrySet();
            for (Map.Entry<String, JsonElement> entry : entries) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (value.isJsonObject()) {
                    map.put(key, this.convertJsonToMap(value.getAsJsonObject()));
                } else if (value.isJsonArray()) {
                    map.put(key, this.convertJsonToArray(value.getAsJsonArray()));
                } else {
                    map.put(key, value.getAsString());
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return map;
    }

    public String convertMapToJson(Map<String,Object> map) {
        return new Gson().toJson(map);
    }
}