package scrapper;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;

import tools.*;

/**
 * 
 * Implements OAuth2 for a Reddit access.
 * 
 * The first time, it retrieves a permanent token (masterToken),
 * via a manual code verification. This token is related to
 * the user of the application, so it must be personal and
 * kept secret.
 * 
 * Then, for each use, it generates a temporary token by
 * refreshing the masterToken.
 * 
 * Doc at {@link https://github.com/reddit/reddit/wiki/OAuth2}
 * 
 * @author Yohan Chalier
 *
 */
public class Authentifier {
	
	private static final String URL_CODE_RETRIEVAL        = "https://www.reddit.com/api/v1/authorize?client_id=CLIENT_ID&response_type=code&state=SEED&redirect_uri=REDIRECT_URI&duration=permanent&scope=read";
	private static final String URL_TOKEN_RETRIEVAL       = "https://www.reddit.com/api/v1/access_token";
	
	private static final String POST_PARAMETERS_RETRIEVAL = "grant_type=authorization_code&code=CODE&redirect_uri=REDIRECT_URI";
	private static final String POST_PARAMETERS_REFRESH   = "grant_type=refresh_token&refresh_token=TOKEN";
		
	private RedditAPI  api;
	
	private String     code;
	private String     seed;
	private JSONObject masterToken;
	private JSONObject secondToken;
	
	private final Object lock = new Object();
		
	/**
	 * The code is stored as its validation is asynchronous.
	 * @param api The Reddit API to link to.
	 */
	public Authentifier(RedditAPI api) {
		this.api  = api;
		this.code = null;
	}
	
	public Object getLock() {
		return lock;
	}
	
	/**
	public Thread getThread() {
		return thread;
	}**/
	
	/**
	 * Tries to refresh the token using the permanent token
	 * stored in FILE_TOKEN (by default token_json).
	 * 
	 * To retrieve the token, either the code has been fetched already,
	 * in which case we just need to send a request to Reddit,
	 * or not, in which case we need to ask for a code.
	 * 
	 * @throws Exception
	 */
	public boolean auth() throws Exception {
		if (!new File(Config.FILE_TOKEN).exists())
			if (code == null) {
				// newAuth = true;
				return retrieveCodeServerOut();
			}
			else
				retrieveToken();
		else 
			loadToken();
		refreshToken();
		
		return false;
		
	}
	
	/**
	 * Generates a random string given a set of characters and a length
	 * 
	 * @param rng Just a new Random() object
	 * @param characters The set of characters to include in the string
	 * @param length The length of the string
	 * @return A random string of length 'length'
	 */
	private String generateString(Random rng, String characters, int length){
	    char[] text = new char[length];
	    for (int i = 0; i < length; i++){
	        text[i] = characters.charAt(rng.nextInt(characters.length()));
	    }
	    return new String(text);
	}
	
	/**
	 * Replaces placeholders in the code retrieval URL,
	 * and store the generated seed for future verification.
	 * See the doc for more info about the seed.
	 * 
	 * @return The code retrieval URL
	 * @throws MalformedURLException
	 */
	private URL getCodeRetrievalURL() throws MalformedURLException {
		seed = generateString(
				new Random(), 
				"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", 
				8);
		return new URL(URL_CODE_RETRIEVAL
				.replace("CLIENT_ID", api.getClientId())
				.replace("REDIRECT_URI", api.getRedirectUri())
				.replace("SEED", seed));
	}
	
	/**
	 * Asks Reddit for a verification code.
	 * 
	 * Generates the URL to retrieve the code from,
	 * and opens URL.
	 * 
	 * The server thread should handle the
	 * response from Reddit online API.
	 * 
	 * @throws Exception
	 * @see Server
	 */
	private boolean retrieveCodeServerOut() throws Exception {
		// Getting the url
		URL url = getCodeRetrievalURL();
		
		// Printing it for manual verification
		Logger.wrI("AUTHENTIFIER", "Code retrieval URL: " + url);
		
		// Automatically opening the browser to allow user's click
		Tools.openBrowser(url.toString());
				
		return true;
	}
	
	/**
	 * Handles the data received by the temporary server
	 * when the redirect_uri request happens.
	 * 
	 * The auth() method previously called that executed
	 * retrieveCodeServerOut has ended.
	 * 
	 * So now that we have the code, we need to re-start
	 * it to retrieve the token (only if the seed matches)
	 * 
	 * @param code The code given by Reddit
	 * @param seed The seed associated with the request,
	 * 			   that must match the stored seed.
	 * @throws Exception
	 */
	public void retrieveCodeServerIn(String code, String seed) 
			throws Exception {
		if (this.seed.equals(seed)) {
			this.code = code;
			auth();
		}
	}
	
	/**
	 * Builds the authentication header for a 
	 * basic HTTP authentication. See doc.
	 * 
	 * @return The string header for the request property
	 * 		   "Authorization"
	 * @see Connection
	 */
	private String getAuthHeader() {
		// Builds the string (see doc)
		String auth = api.getClientId() + ":";
		// Encodes it
		byte[] encodedAuth = Base64.getEncoder().encode(
				auth.getBytes(Charset.forName("US-ASCII"))
				);
		return "Basic " + new String(encodedAuth);
	}
	
	/**
	 * Builds the authentication POST parameters for
	 * the token retrieval once the code has been
	 * retrieved.
	 * 
	 * @return The string containing relevant parameters
	 * 		   according to the doc.
	 * @throws MalformedURLException
	 */
	private String getAuthParametersRetrieval() 
			throws MalformedURLException {
		return POST_PARAMETERS_RETRIEVAL
				.replace("CODE", code)
				.replace("REDIRECT_URI", api.getRedirectUri());
	}
	
	/**
	 * Builds the authentication POST parameters for
	 * the token refreshing.
	 * 
	 * @return The string containing relevant parameters
	 * 		   according to the doc.
	 * @throws MalformedURLException
	 * @throws JSONException
	 */
	private String getAuthParametersRefresh() 
			throws MalformedURLException, JSONException {
		return POST_PARAMETERS_REFRESH
				.replace("TOKEN", masterToken
						.getString("refresh_token"));
	}
	
	/**
	 * Saves the permanent token in a JSON file.
	 * 
	 * @throws IOException
	 * @throws JSONException
	 */
	private void saveToken() throws IOException, JSONException {
		if (masterToken != null)
			JSONHandler.save(masterToken, Config.FILE_TOKEN);
	}
	
	/**
	 * Initializes the connection for the token retrieval/refreshing,
	 * and returns the so obtained response.
	 * 
	 * @param parameters The POST parameters required by the API
	 * @return The received token as a JSON
	 * @throws IOException
	 * @throws JSONException
	 */
	private JSONObject getTokenRetrievalResponse(String parameters)
			throws IOException, JSONException {
		Connection c = new Connection(URL_TOKEN_RETRIEVAL);
		c.initBasicAuthorization(getAuthHeader());
		c.initPOSTRequest(parameters);
		String response = c.getResponse();
		return new JSONObject(response);
	}
	
	/**
	 * The code must be initialized.
	 * Retrieves the permanent token and saves it.
	 * 
	 * @throws IOException
	 * @throws JSONException
	 */
	private void retrieveToken() throws IOException, JSONException {
		masterToken = getTokenRetrievalResponse(
				getAuthParametersRetrieval());
		saveToken();
	}
	
	/**
	 * The masterToken must be initialized.
	 * Refreshes the token.
	 * 
	 * @throws IOException
	 * @throws JSONException
	 */
	private void refreshToken() throws IOException, JSONException {
		Logger.wrI("AUTHENTIFIER", "Refreshing token...");
		secondToken = getTokenRetrievalResponse(
				getAuthParametersRefresh());
		synchronized (lock) {
			lock.notify();				
		}
		Logger.wrI("AUTHENTIFIER", "Token refreshed");
	}
	
	/**
	 * Loads the permanent token from an external file.
	 * 
	 * @throws IOException
	 * @throws JSONException
	 */
	private void loadToken() throws IOException, JSONException {
		masterToken = JSONHandler.load(Config.FILE_TOKEN);
	}
	
	/**
	 * A getter that returns only the string necessary
	 * to the Reddit API.
	 * 
	 * @return
	 * @throws JSONException
	 */
	public String getToken() throws JSONException {
		return secondToken.getString("access_token");
	}

}
