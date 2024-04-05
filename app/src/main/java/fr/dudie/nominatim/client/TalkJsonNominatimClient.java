/*
 * Nominatim Java API client
 *
 * SPDX-FileCopyrightText: 2010 - 2014 Dudie
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package fr.dudie.nominatim.client;

import android.util.Log;

import com.github.filosganga.geogson.gson.GeometryAdapterFactory;
import com.github.filosganga.geogson.jts.JtsAdapterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import fr.dudie.nominatim.client.request.NominatimLookupRequest;
import fr.dudie.nominatim.client.request.NominatimReverseRequest;
import fr.dudie.nominatim.client.request.NominatimSearchRequest;
import fr.dudie.nominatim.client.request.paramhelper.OsmType;
import fr.dudie.nominatim.gson.ArrayOfAddressElementsDeserializer;
import fr.dudie.nominatim.gson.ArrayOfPolygonPointsDeserializer;
import fr.dudie.nominatim.gson.BoundingBoxDeserializer;
import fr.dudie.nominatim.gson.PolygonPointDeserializer;
import fr.dudie.nominatim.model.Address;
import fr.dudie.nominatim.model.BoundingBox;
import fr.dudie.nominatim.model.Element;
import fr.dudie.nominatim.model.PolygonPoint;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * An implementation of the Nominatim Api Service.
 *
 * @author Jérémie Huchet
 * @author Sunil D S
 * @author Andy Scherzinger
 */
public final class TalkJsonNominatimClient implements NominatimClient {
    private static final String TAG = "TalkNominationClient";

    /**
     * UTF-8 encoding.
     */
    public static final String ENCODING_UTF_8 = "UTF-8";

    private final OkHttpClient httpClient;

    /**
     * Gson instance for Nominatim API calls.
     */
    private final Gson gson;

    /**
     * The url to make search queries.
     */
    private final String searchUrl;

    /**
     * The url for reverse geocoding.
     */
    private final String reverseUrl;

    /**
     * The url for address lookup.
     */
    private final String lookupUrl;

    /**
     * The default search options.
     */
    private final NominatimOptions defaults;

    /**
     * Creates the json nominatim client.
     *
     * @param baseUrl    the nominatim server url
     * @param httpClient an HTTP client
     * @param email      an email to add in the HTTP requests parameters to "sign" them (see
     *                   https://wiki.openstreetmap.org/wiki/Nominatim_usage_policy)
     */
    public TalkJsonNominatimClient(final String baseUrl, final OkHttpClient httpClient, final String email) {
        this(baseUrl, httpClient, email, new NominatimOptions());
    }

    /**
     * Creates the json nominatim client.
     *
     * @param baseUrl    the nominatim server url
     * @param httpClient an HTTP client
     * @param email      an email to add in the HTTP requests parameters to "sign" them (see
     *                   https://wiki.openstreetmap.org/wiki/Nominatim_usage_policy)
     * @param defaults   defaults options, they override null valued requests options
     */
    public TalkJsonNominatimClient(final String baseUrl, final OkHttpClient httpClient, final String email, final NominatimOptions defaults) {
        String emailEncoded;
        try {
            emailEncoded = URLEncoder.encode(email, ENCODING_UTF_8);
        } catch (UnsupportedEncodingException e) {
            emailEncoded = email;
        }
        this.searchUrl = String.format("%s/search?format=jsonv2&email=%s", baseUrl.replaceAll("/$", ""), emailEncoded);
        this.reverseUrl = String.format("%s/reverse?format=jsonv2&email=%s", baseUrl.replaceAll("/$", ""), emailEncoded);
        this.lookupUrl = String.format("%s/lookup?format=json&email=%s", baseUrl.replaceAll("/$", ""), emailEncoded);

        Log.d(TAG, "API search URL: " + searchUrl);
        Log.d(TAG, "API reverse URL: " + reverseUrl);

        this.defaults = defaults;

        // prepare gson instance
        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(Element[].class, new ArrayOfAddressElementsDeserializer());
        gsonBuilder.registerTypeAdapter(PolygonPoint.class, new PolygonPointDeserializer());
        gsonBuilder.registerTypeAdapter(PolygonPoint[].class, new ArrayOfPolygonPointsDeserializer());
        gsonBuilder.registerTypeAdapter(BoundingBox.class, new BoundingBoxDeserializer());

        gsonBuilder.registerTypeAdapterFactory(new JtsAdapterFactory());
        gsonBuilder.registerTypeAdapterFactory(new GeometryAdapterFactory());

        gson = gsonBuilder.create();

        // prepare httpclient
        this.httpClient = httpClient;
    }

    /**
     * {@inheritDoc}
     *
     * @see fr.dudie.nominatim.client.NominatimClient#search(fr.dudie.nominatim.client.request.NominatimSearchRequest)
     */
    @Override
    public List<Address> search(final NominatimSearchRequest search) throws IOException {

        defaults.mergeTo(search);
        final String apiCall = String.format("%s&%s", searchUrl, search.getQueryString());
        Log.d(TAG, "search url: " + apiCall);

        Request requesthttp = new Request.Builder()
                .addHeader("accept", "application/json")
                .url(apiCall)
                .build();

        Response response = httpClient.newCall(requesthttp).execute();
        if (response.isSuccessful()) {
            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                return gson.fromJson(responseBody.string(), new TypeToken<List<Address>>() {
                }.getType());
            }
        }

        return new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     *
     * @see fr.dudie.nominatim.client.NominatimClient#getAddress(fr.dudie.nominatim.client.request.NominatimReverseRequest)
     */
    @Override
    public Address getAddress(final NominatimReverseRequest reverse) throws IOException {

        final String apiCall = String.format("%s&%s", reverseUrl, reverse.getQueryString());
        Log.d(TAG, "reverse geocoding url: " + apiCall);

        Request requesthttp = new Request.Builder()
                .addHeader("accept", "application/json")
                .url(apiCall)
                .build();

        Response response = httpClient.newCall(requesthttp).execute();
        if (response.isSuccessful()) {
            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                return gson.fromJson(responseBody.string(), Address.class);
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @see fr.dudie.nominatim.client.NominatimClient#lookupAddress(fr.dudie.nominatim.client.request.NominatimLookupRequest)
     */
    @Override
    public List<Address> lookupAddress(final NominatimLookupRequest lookup) throws IOException {

        final String apiCall = String.format("%s&%s", lookupUrl, lookup.getQueryString());
        Log.d(TAG, "lookup url: " + apiCall);
        Request requesthttp = new Request.Builder()
                .addHeader("accept", "application/json")
                .url(apiCall)
                .build();

        Response response = httpClient.newCall(requesthttp).execute();
        if (response.isSuccessful()) {
            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                return gson.fromJson(responseBody.string(), new TypeToken<List<Address>>() {
                }.getType());
            }
        }

        return new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     *
     * @see fr.dudie.nominatim.client.NominatimClient#search(java.lang.String)
     */
    @Override
    public List<Address> search(final String query) throws IOException {

        final NominatimSearchRequest q = new NominatimSearchRequest();
        q.setQuery(query);
        return this.search(q);
    }

    /**
     * {@inheritDoc}
     *
     * @see fr.dudie.nominatim.client.NominatimClient#getAddress(double, double)
     */
    @Override
    public Address getAddress(final double longitude, final double latitude) throws IOException {

        final NominatimReverseRequest q = new NominatimReverseRequest();
        q.setQuery(longitude, latitude);
        return this.getAddress(q);
    }

    /**
     * {@inheritDoc}
     *
     * @see fr.dudie.nominatim.client.NominatimClient#getAddress(double, double, int)
     */
    @Override
    public Address getAddress(final double longitude, final double latitude, final int zoom)
            throws IOException {

        final NominatimReverseRequest q = new NominatimReverseRequest();
        q.setQuery(longitude, latitude);
        q.setZoom(zoom);
        return this.getAddress(q);
    }

    /**
     * {@inheritDoc}
     *
     * @see fr.dudie.nominatim.client.NominatimClient#getAddress(int, int)
     */
    @Override
    public Address getAddress(final int longitudeE6, final int latitudeE6) throws IOException {

        return this.getAddress((longitudeE6 / 1E6), (latitudeE6 / 1E6));
    }

    /**
     * {@inheritDoc}
     *
     * @see fr.dudie.nominatim.client.NominatimClient#getAddress(String, long)
     */
    @Override
    public Address getAddress(final String type, final long id) throws IOException {

        final NominatimReverseRequest q = new NominatimReverseRequest();
        q.setQuery(OsmType.from(type), id);
        return this.getAddress(q);
    }

    /**
     * {@inheritDoc}
     *
     * @see fr.dudie.nominatim.client.NominatimClient#lookupAddress(java.util.List)
     */
    @Override
    public List<Address> lookupAddress(final List<String> typeId) throws IOException {

        final NominatimLookupRequest q = new NominatimLookupRequest();
        q.setQuery(typeId);
        return this.lookupAddress(q);
    }
}
