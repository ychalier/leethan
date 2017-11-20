package web;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.Regex;

/**
 * 
 * Implements a engine to associate routes and views.
 * 
 * @author Yohan Chalier
 *
 */
public class Router {
	
	private Map<String, View> map;
	private Model  model;
	
	/**
	 * The association is made using a hash map.
	 * 
	 * @param model The model from the MVC scheme to link
	 * 				the views with the data.
	 */
	public Router(Model model) {
		this.model = model;
		map = new HashMap<String, View>();
	}
	
	/**
	 * Adds a view to the map and sets its model to it
	 * 
	 * @param pattern The regular expression of the route
	 * @param view The corresponding view
	 * @see View
	 */
	public void addView(String pattern, View view) {
		view.setModel(model);
		map.put(pattern, view);
	}
	
	/**
	 * Maps a request to a view, by matching
	 * the regular expressions.
	 * 
	 * @param request The GET request
	 * @return The view corresponding
	 * @throws IOException
	 */
	public View findView(String request)  {
		// Removes the GET and the HTTP/1.1
		String route = Regex.parse(Regex.PATTERN_ROUTE, request);
		
		// Splits the hierarchical part from the query part
		String[] split = route.split("\\?");
		String url = split[0];
		String[] query = null;
		if (split.length >= 2) query = split[1].split("&");
		
		// Handling static file
		int index = url.lastIndexOf('.'); // Seek file extension index
		if (index != -1) {
			String extension = url.substring(index); // Get the file extension
			if (extension.matches(Server.STATIC_FILES)) { // If it is supported
				if (new File(Server.STATIC_DIR + url).exists()){
					// Uses a StaticEngine to ease programming
					try {
						return new View(Server.STATIC_DIR + url, new StaticEngine());
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					return null;
				}
			}
		}
		
		List<String> hierarchy;
		View view;
		for(String pattern : map.keySet()) {
			if ((hierarchy = Regex.parseAll(pattern, url)) != null) {
				view = map.get(pattern);
				view.setRequest(hierarchy, query);
				return view;
			}
		}
		return null;
	}

}
