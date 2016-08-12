package edu.ucsb.nceas.mdqengine.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JsonMarshaller {

	private static Gson gson = new GsonBuilder().serializeNulls().create();

	public static String toJson(Object obj) {
		return gson.toJson(obj);
	}
	
	public static Object fromJson(String json, Class clazz) {
		return gson.fromJson(json, clazz);
	}
}
