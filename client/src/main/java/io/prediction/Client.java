package io.prediction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.ning.http.client.*;
import com.ning.http.client.extra.ThrottleRequestFilter;
import com.ning.http.client.providers.netty.NettyAsyncHttpProvider;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.Date;

/**
 * The Client class is a full feature abstraction on top of the RESTful PredictionIO REST API interface.
 * <p>
 * All REST request methods come in both synchronous and asynchronous flavors via method overloading.
 * Synchronization methods are also provided via method overloading.
 * <p>
 * Multiple simultaneous asynchronous requests is made possible by the high performance backend provided by the <a href="https://github.com/AsyncHttpClient/async-http-client">Async Http Client</a>.
 *
 * @author The PredictionIO Team (<a href="http://prediction.io">http://prediction.io</a>)
 * @version 0.3
 * @since 0.1
 */
public class Client {
    private static Logger logger = Logger.getLogger(Client.class);
    // API base URL constant string
    private static final String defaultApiUrl = "http://localhost:8000";
    private static final String apiFormat = "json";
    private static final int defaultThreadLimit = 100;
    // HTTP status code
    private final int HTTP_OK = 200;
    private final int HTTP_CREATED = 201;
    //API Url
    private String apiUrl;
    // Appkey
    private String appkey;
    // Async HTTP client
    private AsyncHttpClientConfig config;
    private AsyncHttpClient client;

    /**
     * Instantiate a PredictionIO RESTful API client using default values for API URL and thread limit.
     * <p>
     * The default API URL is http://localhost:8000. The default thread limit is 100.
     *
     * @param appkey the app key that this client will use to communicate with the API
     */
    public Client(String appkey) {
        this(appkey, Client.defaultApiUrl, Client.defaultThreadLimit);
    }

    /**
     * Instantiate a PredictionIO RESTful API client using default values for API URL.
     * <p>
     * The default API URL is http://localhost:8000.
     *
     * @param appkey the app key that this client will use to communicate with the API
     * @param apiURL the URL of the PredictionIO API
     */
    public Client(String appkey, String apiURL) {
        this(appkey, apiURL, Client.defaultThreadLimit);
    }

    /**
     * Instantiate a PredictionIO RESTful API client.
     *
     * @param appkey the app key that this client will use to communicate with the API
     * @param apiURL the URL of the PredictionIO API
     * @param threadLimit maximum number of simultaneous threads (connections) to the API
     */
    public Client(String appkey, String apiURL, int threadLimit) {
        if (apiURL != null) {
            this.apiUrl = apiURL;
        } else {
            this.apiUrl = defaultApiUrl;
        }
        this.setAppkey(appkey);
        // Async HTTP client config
        this.config = (new AsyncHttpClientConfig.Builder())
            .setAllowPoolingConnection(true)
            .setAllowSslConnectionPool(true)
            .addRequestFilter(new ThrottleRequestFilter(threadLimit))
            .setMaximumConnectionsPerHost(threadLimit)
            .setRequestTimeoutInMs(10000)
            .setIOThreadMultiplier(threadLimit)
            .build();
        this.client = new AsyncHttpClient(new NettyAsyncHttpProvider(config), config);
    }

    private AsyncHandler<APIResponse> getHandler() {
        return new AsyncHandler<APIResponse>() {
            private final Response.ResponseBuilder builder = new Response.ResponseBuilder();

            public void onThrowable(Throwable t) {
            }

            public STATE onBodyPartReceived(HttpResponseBodyPart content) throws Exception {
                builder.accumulate(content);
                return STATE.CONTINUE;
            }

            public STATE onStatusReceived(HttpResponseStatus status) throws Exception {
                builder.accumulate(status);
                return STATE.CONTINUE;
            }

            public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
                builder.accumulate(headers);
                return STATE.CONTINUE;
            }

            public APIResponse onCompleted() throws Exception {
                Response r = builder.build();
                return new APIResponse(r.getStatusCode(), r.getResponseBody());
            }
        };
    }

    private String[] jsonArrayAsStringArray(JsonArray a) throws ClassCastException {
        int l = a.size();
        String[] r = new String[l];
        for (int i = 0; i < l; i++) {
            r[i] = a.get(i).getAsString();
        }
        return r;
    }

    private int[] jsonArrayAsIntArray(JsonArray a) throws ClassCastException {
        int l = a.size();
        int[] r = new int[l];
        for (int i = 0; i < l; i++) {
            r[i] = a.get(i).getAsInt();
        }
        return r;
    }

    private double[] jsonArrayAsDoubleArray(JsonArray a) throws ClassCastException {
        int l = a.size();
        double[] r = new double[l];
        for (int i = 0; i < l; i++) {
            r[i] = a.get(i).getAsDouble();
        }
        return r;
    }

    /**
     * Set the app key of this client.
     * <p>
     * All subsequent requests after this method will use the new app key.
     *
     * @param appkey the new app key to be used
     */
    public void setAppkey(String appkey) {
        // Set current API's appkey
        this.appkey = appkey;
    }

    /**
     * Get status of the API.
     */
    public FutureAPIResponse getStatus() throws IOException {
        return new FutureAPIResponse(this.client.prepareGet(this.apiUrl).execute(this.getHandler()));
    }

    /**
     * Get a create user request builder that can be used to add additional user attributes.
     *
     * @param uid ID of the User to be created
     */
    public CreateUserRequestBuilder getCreateUserRequestBuilder(String uid) {
        return new CreateUserRequestBuilder(this.apiUrl, this.apiFormat, this.appkey, uid);
    }

    /**
     * Sends an asynchronous create user request to the API.
     *
     * @param builder an instance of {@link CreateUserRequestBuilder} that will be turned into a request
     */
    public FutureAPIResponse createUserAsFuture(CreateUserRequestBuilder builder) throws IOException {
        return new FutureAPIResponse(this.client.executeRequest(builder.build(), this.getHandler()));
    }

    /**
     * Sends a synchronous create user request to the API.
     *
     * @param uid ID of the User to be created
     */
    public boolean createUser(String uid) throws IOException {
        return this.createUser(this.createUserAsFuture(this.getCreateUserRequestBuilder(uid)));
    }

    /**
     * Sends a synchronous create user request to the API.
     *
     * @param builder an instance of {@link CreateUserRequestBuilder} that will be turned into a request
     */
    public boolean createUser(CreateUserRequestBuilder builder) throws IOException {
        return this.createUser(this.createUserAsFuture(builder));
    }

    /**
     * Synchronize a previously sent asynchronous create user request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#createUserAsFuture}
     */
    public boolean createUser(FutureAPIResponse response) {
        int status = response.getStatus();

        if (status == this.HTTP_CREATED) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Sends an asynchronous get user request to the API.
     *
     * @param uid ID of the User to get
     */
    public FutureAPIResponse getUserAsFuture(String uid) throws IOException {
        Request request = (new RequestBuilder("GET")).setUrl(this.apiUrl + "/users/" + uid + "." + this.apiFormat).addQueryParameter("appkey", this.appkey).build();
        return new FutureAPIResponse(this.client.executeRequest(request, this.getHandler()));
    }

    /**
     * Sends a synchronous get user request to the API.
     *
     * @param uid ID of the User to get
     */
    public User getUser(String uid) throws IOException {
        return this.getUser(this.getUserAsFuture(uid));
    }

    /**
     * Synchronize a previously sent asynchronous get user request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#getUserAsFuture}
     */
    public User getUser(FutureAPIResponse response) {
        int status = response.getStatus();

        if (status == this.HTTP_OK) {
            String message = response.getMessage();
            try {
                JsonParser parser = new JsonParser();
                JsonObject messageAsJson = (JsonObject) parser.parse(message);
                String uid = messageAsJson.get("uid").getAsString();
                User user = new User(uid);
                user.created(new DateTime(messageAsJson.get("ct").getAsLong()));
                //Check values
                try {
                    if (messageAsJson.getAsJsonArray("latlng") != null) {
                        double latlng[] = this.jsonArrayAsDoubleArray(messageAsJson.getAsJsonArray("latlng"));
                        user.latitude(new Double(latlng[0]));
                        user.longitude(new Double(latlng[1]));
                    }
                } catch (ClassCastException cce) {
                    if (logger.isDebugEnabled()) {
                        logger.error("ClassCastException occurred trying to get latlng");
                        logger.error(cce.getMessage());
                    }
                } catch (Exception e) {
                    if (logger.isDebugEnabled()) {
                        logger.error("Exception occurred trying to get latlng");
                        logger.error(e.getMessage());
                    }
                }
                return user;
            } catch (JsonParseException e) {
                if (logger.isDebugEnabled()) {
                    logger.error("JsonParseException occurred");
                    logger.error(e.getMessage());
                }
                return null;
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.error("General Exception occurred in getUser");
                    logger.error(e.getMessage());
                }
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Sends an asynchronous delete user request to the API.
     *
     * @param uid ID of the User to be deleted
     */
    public FutureAPIResponse deleteUserAsFuture(String uid) throws IOException {
        RequestBuilder builder = new RequestBuilder("DELETE");
        builder.setUrl(this.apiUrl + "/users/" + uid + "." + this.apiFormat);
        builder.addQueryParameter("appkey", this.appkey);
        return new FutureAPIResponse(this.client.executeRequest(builder.build(), this.getHandler()));
    }

    /**
     * Sends a synchronous delete user request to the API.
     *
     * @param uid ID of the User to be deleted
     */
    public boolean deleteUser(String uid) throws IOException {
        return this.deleteUser(this.deleteUserAsFuture(uid));
    }

    /**
     * Synchronize a previously sent asynchronous delete user request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#deleteUserAsFuture}
     */
    public boolean deleteUser(FutureAPIResponse response) {
        int status = response.getStatus();

        if (status == this.HTTP_OK) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get a create item request builder that can be used to add additional item attributes.
     *
     * @param iid ID of the Item to be created
     * @param itypes array of types of the Item
     */
    public CreateItemRequestBuilder getCreateItemRequestBuilder(String iid, String[] itypes) {
        return new CreateItemRequestBuilder(this.apiUrl, this.apiFormat, this.appkey, iid, itypes);
    }

    /**
     * Sends an asynchronous create item request to the API.
     *
     * @param builder an instance of {@link CreateItemRequestBuilder} that will be turned into a request
     */
    public FutureAPIResponse createItemAsFuture(CreateItemRequestBuilder builder) throws IOException {
        return new FutureAPIResponse(this.client.executeRequest(builder.build(), this.getHandler()));
    }

    /**
     * Sends a synchronous create item request to the API.
     *
     * @param iid ID of the Item to be created
     * @param itypes array of types of the Item
     */
    public boolean createItem(String iid, String[] itypes) throws IOException {
        return this.createItem(this.createItemAsFuture(this.getCreateItemRequestBuilder(iid, itypes)));
    }

    /**
     * Sends a synchronous create item request to the API.
     *
     * @param builder an instance of {@link CreateItemRequestBuilder} that will be turned into a request
     */
    public boolean createItem(CreateItemRequestBuilder builder) throws IOException {
        return this.createItem(this.createItemAsFuture(builder));
    }

    /**
     * Synchronize a previously sent asynchronous create item request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#createItemAsFuture}
     */
    public boolean createItem(FutureAPIResponse response) {
        int status = response.getStatus();

        if (status == this.HTTP_CREATED) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Sends an asynchronous get item request to the API.
     *
     * @param iid ID of the Item to get
     */
    public FutureAPIResponse getItemAsFuture(String iid) throws IOException {
        Request request = (new RequestBuilder("GET")).setUrl(this.apiUrl + "/items/" + iid + "." + this.apiFormat).addQueryParameter("appkey", this.appkey).build();
        return new FutureAPIResponse(this.client.executeRequest(request, this.getHandler()));
    }

    /**
     * Sends a synchronous get item request to the API.
     *
     * @param iid ID of the Item to get
     */
    public Item getItem(String iid) throws IOException {
        return this.getItem(this.getItemAsFuture(iid));
    }

    /**
     * Synchronize a previously sent asynchronous get item request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#getItemAsFuture}
     */
    public Item getItem(FutureAPIResponse response) {
        int status = response.getStatus();

        if (status == this.HTTP_OK) {
            String message = response.getMessage();
            try {
                JsonParser parser = new JsonParser();
                JsonObject messageAsJson = (JsonObject) parser.parse(message);
                String iid = messageAsJson.get("iid").getAsString();
                String[] itypes = this.jsonArrayAsStringArray(messageAsJson.getAsJsonArray("itypes"));
                Item item = new Item(iid, itypes);
                item.created(new DateTime(messageAsJson.get("ct").getAsLong()));
                try {
                    if (messageAsJson.get("startT") != null) {
                        item.startT(new Date(messageAsJson.get("startT").getAsLong()));
                    }
                } catch (ClassCastException cce) {
                    if (logger.isDebugEnabled()) {
                        logger.error("ClassCastException occurred trying to get startT");
                        logger.error(cce.getMessage());
                    }
                } catch (Exception e) {
                    if (logger.isDebugEnabled()) {
                        logger.error("Exception occurred trying to get startT");
                        logger.error(e.getMessage());
                    }
                }
                try {
                    if (messageAsJson.get("endT") != null) {
                        item.endT(new Date(messageAsJson.get("endT").getAsLong()));
                    }
                } catch (ClassCastException cce) {
                    if (logger.isDebugEnabled()) {
                        logger.error("ClassCastException occurred trying to get endT");
                        logger.error(cce.getMessage());
                    }
                } catch (Exception e) {
                    if (logger.isDebugEnabled()) {
                        logger.error("Exception occurred trying to get endT");
                        logger.error(e.getMessage());
                    }
                }
                try {
                    if (messageAsJson.getAsJsonArray("latlng") != null) {
                        double latlng[] = this.jsonArrayAsDoubleArray(messageAsJson.getAsJsonArray("latlng"));
                        item.latitude(new Double(latlng[0]));
                        item.longitude(new Double(latlng[1]));
                    }
                } catch (ClassCastException cce) {
                    if (logger.isDebugEnabled()) {
                        logger.error("ClassCastException occurred trying to get latlng");
                        logger.error(cce.getMessage());
                    }
                } catch (Exception e) {
                    if (logger.isDebugEnabled()) {
                        logger.error("Exception occurred trying to get latlng");
                        logger.error(e.getMessage());
                    }
                }
                return item;
            } catch (JsonParseException e) {
                if (logger.isDebugEnabled()) {
                    logger.error("JsonParseException occurred");
                    logger.error(e.getMessage());
                }
                return null;
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.error("General exception occurred in getItem");
                    logger.error(e.getMessage());
                }
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Sends an asynchronous delete item request to the API.
     *
     * @param iid ID of the Item to be deleted
     */
    public FutureAPIResponse deleteItemAsFuture(String iid) throws IOException {
        RequestBuilder builder = new RequestBuilder("DELETE");
        builder.setUrl(this.apiUrl + "/items/" + iid + "." + this.apiFormat);
        builder.addQueryParameter("appkey", this.appkey);
        return new FutureAPIResponse(this.client.executeRequest(builder.build(), this.getHandler()));
    }

    /**
     * Sends a synchronous delete item request to the API.
     *
     * @param iid ID of the Item to be deleted
     */
    public boolean deleteItem(String iid) throws IOException {
        return this.deleteItem(this.deleteItemAsFuture(iid));
    }

    /**
     * Synchronize a previously sent asynchronous delete item request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#deleteItemAsFuture}
     */
    public boolean deleteItem(FutureAPIResponse response) {
        int status = response.getStatus();

        if (status == this.HTTP_OK) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get a get recommendations request builder that can be used to add additional request parameters.
     *
     * @param engine engine name
     * @param uid ID of the User whose recommendations will be gotten
     * @param n number of top recommendations to get
     */
    public RecommendationsRequestBuilder getRecommendationsRequestBuilder(String engine, String uid, int n) {
        return new RecommendationsRequestBuilder(this.apiUrl, this.apiFormat, this.appkey, engine, uid, n);
    }

    /**
     * Sends an asynchronous get recommendations request to the API.
     *
     * @param builder an instance of {@link RecommendationsRequestBuilder} that will be turned into a request
     */
    public FutureAPIResponse getRecommendationsAsFuture(RecommendationsRequestBuilder builder) throws IOException {
        return new FutureAPIResponse(this.client.executeRequest(builder.build(), this.getHandler()));
    }

    /**
     * Sends a synchronous get recommendations request to the API.
     *
     * @param engine engine name
     * @param uid ID of the User whose recommendations will be gotten
     * @param n number of top recommendations to get
     */
    public String[] getRecommendations(String engine, String uid, int n) throws IOException {
        return this.getRecommendations(this.getRecommendationsAsFuture(this.getRecommendationsRequestBuilder(engine, uid, n)));
    }

    /**
     * Sends a synchronous get recommendations request to the API.
     *
     * @param builder an instance of {@link RecommendationsRequestBuilder} that will be turned into a request
     */
    public String[] getRecommendations(RecommendationsRequestBuilder builder) throws IOException {
        return this.getRecommendations(this.getRecommendationsAsFuture(builder));
    }

    /**
     * Synchronize a previously sent asynchronous get recommendations request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#getRecommendationsAsFuture}
     */
    public String[] getRecommendations(FutureAPIResponse response) {
        int status = response.getStatus();

        if (status == this.HTTP_OK) {
            String message = response.getMessage();
            try {
                //New implementation using gson
                JsonParser parser = new JsonParser();
                JsonObject messageAsJson = (JsonObject) parser.parse(message);
                JsonArray iidsAsJson = messageAsJson.getAsJsonArray("iids");
                return this.jsonArrayAsStringArray(iidsAsJson);
//                JSONObject messageAsJson = new JSONObject(message);
//                JSONArray iidsAsJson = messageAsJson.getJSONArray("iids");
//                return this.jsonArrayAsStringArray(iidsAsJson);
            } catch (JsonParseException e) {
                if (logger.isDebugEnabled()) {
                    logger.error("JsonParseException occurred in getRecommendations");
                    logger.error(e.getMessage());
                }
                return new String[0];
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.error("General Exception occurred trying to get getRecommendations");
                    logger.error(e.getMessage());
                }
                return new String[0];
            }
        } else {
            return new String[0];
        }
    }

    /**
     * Future functionality.
     */
    public SimilarRequestBuilder getSimilarRequestBuilder(String iid, int n) {
        return new SimilarRequestBuilder(this.apiUrl, this.apiFormat, this.appkey, iid, n);
    }

    /**
     * Future functionality.
     */
    public FutureAPIResponse getSimilarAsFuture(SimilarRequestBuilder builder) throws IOException {
        return new FutureAPIResponse(this.client.executeRequest(builder.build(), this.getHandler()));
    }

    /**
     * Future functionality.
     */
    public String[] getSimilar(String iid, int n) throws IOException {
        return this.getSimilar(this.getSimilarAsFuture(this.getSimilarRequestBuilder(iid, n)));
    }

    /**
     * Future functionality.
     */
    public String[] getSimilar(SimilarRequestBuilder builder) throws IOException {
        return this.getSimilar(this.getSimilarAsFuture(builder));
    }

    /**
     * Future functionality.
     */
    public String[] getSimilar(FutureAPIResponse response) {
        int status = response.getStatus();

        if (status == this.HTTP_OK) {
            String message = response.getMessage();
            try {
                //New implementation using gson
                JsonParser parser = new JsonParser();
                JsonObject messageAsJson = (JsonObject) parser.parse(message);
                JsonArray iidsAsJson = messageAsJson.getAsJsonArray("iids");
                return this.jsonArrayAsStringArray(iidsAsJson);
            } catch (JsonParseException e) {
                if (logger.isDebugEnabled()) {
                    logger.error("JsonParseException occurred trying to get getSimilar");
                    logger.error(e.getMessage());
                }
                return new String[0];
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.error("General Exception occurred trying to get getSimilar");
                    logger.error(e.getMessage());
                }
                return new String[0];
            }
        } else {
            return new String[0];
        }
    }

    /**
     * Get a user-rate-item action request builder that can be used to add additional request parameters.
     *
     * @param uid ID of the User of this action
     * @param iid ID of the Item of this action
     * @param rate the rating of this action
     */
    public UserActionItemRequestBuilder getUserRateItemRequestBuilder(String uid, String iid, int rate) {
        UserActionItemRequestBuilder builder = new UserActionItemRequestBuilder(this.apiUrl, this.apiFormat, this.appkey, UserActionItemRequestBuilder.RATE, uid, iid);
        builder.rate(rate);
        return builder;
    }

    /**
     * Sends an asynchronous user-rate-item action request to the API.
     *
     * @param builder an instance of {@link UserActionItemRequestBuilder} that will be turned into a request
     */
    public FutureAPIResponse userRateItemAsFuture(UserActionItemRequestBuilder builder) throws IOException {
        return new FutureAPIResponse(this.client.executeRequest(builder.build(), this.getHandler()));
    }

    /**
     * Sends a synchronous user-rate-item action request to the API.
     *
     * @param uid ID of the User of this action
     * @param iid ID of the Item of this action
     * @param rate the rating of this action
     */
    public boolean userRateItem(String uid, String iid, int rate) throws IOException {
        return this.userRateItem(this.userRateItemAsFuture(this.getUserRateItemRequestBuilder(uid, iid, rate)));
    }

    /**
     * Sends a synchronous user-rate-item action request to the API.
     *
     * @param builder an instance of {@link UserActionItemRequestBuilder} that will be turned into a request
     */
    public boolean userRateItem(UserActionItemRequestBuilder builder) throws IOException {
        return this.userRateItem(this.userRateItemAsFuture(builder));
    }

    /**
     * Synchronize a previously sent asynchronous user-rate-item action request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#userRateItemAsFuture}
     */
    public boolean userRateItem(FutureAPIResponse response) {
        if (response.getStatus() == this.HTTP_CREATED) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get a user-like-item action request builder that can be used to add additional request parameters.
     *
     * @param uid ID of the User of this action
     * @param iid ID of the Item of this action
     */
    public UserActionItemRequestBuilder getUserLikeItemRequestBuilder(String uid, String iid) {
        return new UserActionItemRequestBuilder(this.apiUrl, this.apiFormat, this.appkey, UserActionItemRequestBuilder.LIKE, uid, iid);
    }

    /**
     * Sends an asynchronous user-like-item action request to the API.
     *
     * @param builder an instance of {@link UserActionItemRequestBuilder} that will be turned into a request
     */
    public FutureAPIResponse userLikeItemAsFuture(UserActionItemRequestBuilder builder) throws IOException {
        return new FutureAPIResponse(this.client.executeRequest(builder.build(), this.getHandler()));
    }

    /**
     * Sends a synchronous user-like-item action request to the API.
     *
     * @param uid ID of the User of this action
     * @param iid ID of the Item of this action
     */
    public boolean userLikeItem(String uid, String iid) throws IOException {
        return this.userLikeItem(this.userLikeItemAsFuture(this.getUserLikeItemRequestBuilder(uid, iid)));
    }

    /**
     * Sends a synchronous user-like-item action request to the API.
     *
     * @param builder an instance of {@link UserActionItemRequestBuilder} that will be turned into a request
     */
    public boolean userLikeItem(UserActionItemRequestBuilder builder) throws IOException {
        return this.userLikeItem(this.userLikeItemAsFuture(builder));
    }

    /**
     * Synchronize a previously sent asynchronous user-like-item action request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#userLikeItemAsFuture}
     */
    public boolean userLikeItem(FutureAPIResponse response) {
        if (response.getStatus() == this.HTTP_CREATED) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get a user-dislike-item action request builder that can be used to add additional request parameters.
     *
     * @param uid ID of the User of this action
     * @param iid ID of the Item of this action
     */
    public UserActionItemRequestBuilder getUserDislikeItemRequestBuilder(String uid, String iid) {
        return new UserActionItemRequestBuilder(this.apiUrl, this.apiFormat, this.appkey, UserActionItemRequestBuilder.DISLIKE, uid, iid);
    }

    /**
     * Sends an asynchronous user-dislike-item action request to the API.
     *
     * @param builder an instance of {@link UserActionItemRequestBuilder} that will be turned into a request
     */
    public FutureAPIResponse userDislikeItemAsFuture(UserActionItemRequestBuilder builder) throws IOException {
        return new FutureAPIResponse(this.client.executeRequest(builder.build(), this.getHandler()));
    }

    /**
     * Sends a synchronous user-dislike-item action request to the API.
     *
     * @param uid ID of the User of this action
     * @param iid ID of the Item of this action
     */
    public boolean userDislikeItem(String uid, String iid) throws IOException {
        return this.userDislikeItem(this.userDislikeItemAsFuture(this.getUserDislikeItemRequestBuilder(uid, iid)));
    }

    /**
     * Sends a synchronous user-dislike-item action request to the API.
     *
     * @param builder an instance of {@link UserActionItemRequestBuilder} that will be turned into a request
     */
    public boolean userDislikeItem(UserActionItemRequestBuilder builder) throws IOException {
        return this.userDislikeItem(this.userDislikeItemAsFuture(builder));
    }

    /**
     * Synchronize a previously sent asynchronous user-dislike-item action request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#userDislikeItemAsFuture}
     */
    public boolean userDislikeItem(FutureAPIResponse response) {
        if (response.getStatus() == this.HTTP_CREATED) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get a user-view-item action request builder that can be used to add additional request parameters.
     *
     * @param uid ID of the User of this action
     * @param iid ID of the Item of this action
     */
    public UserActionItemRequestBuilder getUserViewItemRequestBuilder(String uid, String iid) {
        return new UserActionItemRequestBuilder(this.apiUrl, this.apiFormat, this.appkey, UserActionItemRequestBuilder.VIEW, uid, iid);
    }

    /**
     * Sends an asynchronous user-view-item action request to the API.
     *
     * @param builder an instance of {@link UserActionItemRequestBuilder} that will be turned into a request
     */
    public FutureAPIResponse userViewItemAsFuture(UserActionItemRequestBuilder builder) throws IOException {
        return new FutureAPIResponse(this.client.executeRequest(builder.build(), this.getHandler()));
    }

    /**
     * Sends a synchronous user-view-item action request to the API.
     *
     * @param uid ID of the User of this action
     * @param iid ID of the Item of this action
     */
    public boolean userViewItem(String uid, String iid) throws IOException {
        return this.userViewItem(this.userViewItemAsFuture(this.getUserViewItemRequestBuilder(uid, iid)));
    }

    /**
     * Sends a synchronous user-view-item action request to the API.
     *
     * @param builder an instance of {@link UserActionItemRequestBuilder} that will be turned into a request
     */
    public boolean userViewItem(UserActionItemRequestBuilder builder) throws IOException {
        return this.userViewItem(this.userViewItemAsFuture(builder));
    }

    /**
     * Synchronize a previously sent asynchronous user-view-item action request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#userViewItemAsFuture}
     */
    public boolean userViewItem(FutureAPIResponse response) {
        if (response.getStatus() == this.HTTP_CREATED) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get a user-conversion-item action request builder that can be used to add additional request parameters.
     *
     * @param uid ID of the User of this action
     * @param iid ID of the Item of this action
     */
    public UserActionItemRequestBuilder getUserConversionItemRequestBuilder(String uid, String iid) {
        return new UserActionItemRequestBuilder(this.apiUrl, this.apiFormat, this.appkey, UserActionItemRequestBuilder.CONVERSION, uid, iid);
    }

    /**
     * Sends an asynchronous user-conversion-item action request to the API.
     *
     * @param builder an instance of {@link UserActionItemRequestBuilder} that will be turned into a request
     */
    public FutureAPIResponse userConversionItemAsFuture(UserActionItemRequestBuilder builder) throws IOException {
        return new FutureAPIResponse(this.client.executeRequest(builder.build(), this.getHandler()));
    }

    /**
     * Sends a synchronous user-conversion-item action request to the API.
     *
     * @param uid ID of the User of this action
     * @param iid ID of the Item of this action
     */
    public boolean userConversionItem(String uid, String iid) throws IOException {
        return this.userConversionItem(this.userConversionItemAsFuture(this.getUserConversionItemRequestBuilder(uid, iid)));
    }

    /**
     * Sends a synchronous user-conversion-item action request to the API.
     *
     * @param builder an instance of {@link UserActionItemRequestBuilder} that will be turned into a request
     */
    public boolean userConversionItem(UserActionItemRequestBuilder builder) throws IOException {
        return this.userConversionItem(this.userConversionItemAsFuture(builder));
    }

    /**
     * Synchronize a previously sent asynchronous user-conversion-item action request.
     *
     * @param response an instance of {@link FutureAPIResponse} returned from {@link Client#userConversionItemAsFuture}
     */
    public boolean userConversionItem(FutureAPIResponse response) {
        if (response.getStatus() == this.HTTP_CREATED) {
            return true;
        } else {
            return false;
        }
    }
}

