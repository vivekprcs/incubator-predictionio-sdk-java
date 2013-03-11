package io.prediction;

import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;

/**
 * Class to build Recommendation Requests
 *
 * @author The PredictionIO Team (<a href="http://prediction.io">http://prediction.io</a>)
 * @version 0.3
 * @since 0.2
 */

public class RecommendationsRequestBuilder {
    private String apiUrl;
    private String apiFormat;
    private String appkey;
    private String engine;
    private String uid;
    private int n;
    private String[] itypes;
    private Double latitude;
    private Double longitude;
    private Double within;
    private String unit;

    public RecommendationsRequestBuilder(String apiUrl, String apiFormat, String appkey, String engine, String uid, int n) {
        this.apiUrl = apiUrl;
        this.apiFormat = apiFormat;
        this.appkey = appkey;
        this.engine = engine;
        this.uid = uid;
        this.n = n;
    }

    public RecommendationsRequestBuilder itypes(String[] itypes) {
        this.itypes = itypes;
        return this;
    }

    public RecommendationsRequestBuilder latitude(Double latitude) {
        this.latitude = latitude;
        return this;
    }

    public RecommendationsRequestBuilder longitude(Double longitude) {
        this.longitude = longitude;
        return this;
    }

    public RecommendationsRequestBuilder within(Double within) {
        this.within = within;
        return this;
    }

    public RecommendationsRequestBuilder unit(String unit) {
        this.unit = unit;
        return this;
    }

    public Request build() {
        RequestBuilder builder = new RequestBuilder("GET");
        builder.setUrl(this.apiUrl + "/engines/itemrec/" + this.engine + "/topn." + this.apiFormat);
        builder.addQueryParameter("appkey", this.appkey);
        builder.addQueryParameter("uid", this.uid);
        builder.addQueryParameter("n", Integer.toString(this.n));
        if (this.itypes != null && this.itypes.length > 0) {
            builder.addQueryParameter("itypes", Utils.itypesAsString(this.itypes));
        }
        if (this.latitude != null && this.longitude != null) {
            builder.addQueryParameter("latlng", this.latitude.toString() + "," + this.longitude.toString());
        }
        if (this.within != null) {
            builder.addQueryParameter("within", this.within.toString());
        }
        if (this.unit != null) {
            builder.addQueryParameter("unit", this.unit.toString());
        }
        return builder.build();
    }
}
