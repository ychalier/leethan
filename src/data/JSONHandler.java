package data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

public class JSONHandler {
	
	public static void save(JSONObject json, String filename) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
		bw.write(json.toString());
		bw.close();
	}
	
	public static JSONObject load(String filename) throws IOException, JSONException {
		BufferedReader br = new BufferedReader(new FileReader(filename));
		String line;
		StringBuffer sb = new StringBuffer();
		while((line = br.readLine()) != null) sb.append(line);
		br.close();
		return new JSONObject(sb.toString());
	}

}