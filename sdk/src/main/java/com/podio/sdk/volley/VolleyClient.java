/*
 *  Copyright (C) 2014 Copyright Citrix Systems, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of 
 *  this software and associated documentation files (the "Software"), to deal in 
 *  the Software without restriction, including without limitation the rights to 
 *  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies 
 *  of the Software, and to permit persons to whom the Software is furnished to 
 *  do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all 
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, 
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE 
 *  SOFTWARE.
 */
package com.podio.sdk.volley;

import java.util.HashMap;
import java.util.Set;

import javax.net.ssl.SSLSocketFactory;

import android.content.Context;
import android.net.Uri;

import com.android.volley.Cache;
import com.android.volley.RequestQueue;
import com.android.volley.RequestQueue.RequestFilter;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.Volley;
import com.podio.sdk.Client;
import com.podio.sdk.Filter;
import com.podio.sdk.JsonParser;
import com.podio.sdk.Request;
import com.podio.sdk.Request.AuthErrorListener;
import com.podio.sdk.Request.ErrorListener;
import com.podio.sdk.Request.ResultListener;
import com.podio.sdk.Request.SessionListener;
import com.podio.sdk.Session;
import com.podio.sdk.internal.Utils;

public class VolleyClient implements Client {

    static class AuthPath extends Filter {

        protected AuthPath() {
            super("oauth/token");
        }

        AuthPath withClientCredentials(String clientId, String clientSecret) {
            addQueryParameter("client_id", clientId);
            addQueryParameter("client_secret", clientSecret);
            return this;
        }

        AuthPath withUserCredentials(String username, String password) {
            addQueryParameter("grant_type", "password");
            addQueryParameter("username", username);
            addQueryParameter("password", password);
            return this;
        }

        AuthPath withAppCredentials(String appId, String appToken) {
            addQueryParameter("grant_type", "app");
            addQueryParameter("app_id", appId);
            addQueryParameter("app_token", appToken);
            return this;
        }

        AuthPath withRefreshToken(String refreshToken) {
            addQueryParameter("grant_type", "refresh_token");
            addQueryParameter("refresh_token", refreshToken);
            return this;
        }

    }

    protected String clientId;
    protected String clientSecret;
    protected String scheme;
    protected String authority;

    private static RequestQueue volleyRequestQueue;
    private static RequestQueue volleyPriorityQueue;

    @Override
    public Request<Void> authenticateWithUserCredentials(String username, String password) {
        Uri uri = new AuthPath()
                .withClientCredentials(clientId, clientSecret)
                .withUserCredentials(username, password)
                .buildUri(scheme, authority);

        String url = parseUrl(uri);
        HashMap<String, String> params = parseParams(uri);
        VolleyRequest<Void> request = VolleyRequest.newAuthRequest(url, params);
        volleyPriorityQueue.add(request);

        return request;
    }

    @Override
    public Request<Void> authenticateWithAppCredentials(String appId, String appToken) {
        Uri uri = new AuthPath()
                .withClientCredentials(clientId, clientSecret)
                .withAppCredentials(appId, appToken)
                .buildUri(scheme, authority);

        String url = parseUrl(uri);
        HashMap<String, String> params = parseParams(uri);
        VolleyRequest<Void> request = VolleyRequest.newAuthRequest(url, params);
        volleyPriorityQueue.add(request);

        return request;
    }

    @Override
    @Deprecated
    public Request<Void> forceRefreshTokens() {
        volleyRequestQueue.stop();

        // Prepare to re-authenticate.
        Uri uri = buildAuthUri();

        // Opt out if we can't re-authenticate.
        if (uri == null) {
            clearRequestQueue();
            volleyRequestQueue.start();
            return null;
        }

        String url = parseUrl(uri);
        HashMap<String, String> params = parseParams(uri);
        VolleyRequest<Void> authRequest = VolleyRequest
                .newAuthRequest(url, params)
                .withAuthErrorListener(new AuthErrorListener<Void>() {
                    @Override
                    public boolean onAuthErrorOccured(Request<Void> originalRequest) {
                        volleyRequestQueue.start();
                        return false;
                    }
                })
                .withResultListener(new ResultListener<Void>() {

                    @Override
                    public boolean onRequestPerformed(Void nothing) {
                        volleyRequestQueue.start();
                        return false;
                    }
                })
                .withSessionListener(new SessionListener() {

                    @Override
                    public boolean onSessionChanged(String authToken, String refreshToken, long expires) {
                        volleyRequestQueue.start();
                        return false;
                    }

                })
                .withErrorListener(new ErrorListener() {

                    @Override
                    public boolean onErrorOccured(Throwable cause) {
                        volleyRequestQueue.start();
                        return false;
                    }

                });

        // Re-authenticate on a prioritized request queue.
        volleyPriorityQueue.add(authRequest);

        return authRequest;
    }

    @Override
    public <T> Request<T> request(Request.Method method, Filter filter, Object item, Class<T> classOfItem) {
        // Prepare the request.
        String url = filter.buildUri(scheme, authority).toString();
        String body = item != null ? JsonParser.toJson(item) : null;
        VolleyRequest<T> request = VolleyRequest.newRequest(method, url, body, classOfItem);

        // Make us aware of any authentication errors.
        request.withAuthErrorListener(new AuthErrorListener<T>() {

            @Override
            public boolean onAuthErrorOccured(final Request<T> originalRequest) {
                // The original request failed due to an authentication error.
                // Stop executing the default HTTP queue as we currently don't
                // have valid auth tokens.
                ((VolleyRequest<T>) originalRequest).removeAuthErrorListener(this);
                volleyRequestQueue.stop();

                // Prepare to re-authenticate.
                Uri uri = buildAuthUri();

                // Opt out if we can't re-authenticate.
                if (uri == null) {
                    clearRequestQueue();
                    volleyRequestQueue.start();
                    return true;
                }

                String url = parseUrl(uri);
                HashMap<String, String> params = parseParams(uri);

                // Re-authenticate on a prioritized request queue.
                volleyPriorityQueue.add(VolleyRequest
                        .newAuthRequest(url, params)
                        .withErrorListener(new ErrorListener() {

                            @Override
                            @SuppressWarnings("unchecked")
                            public boolean onErrorOccured(Throwable cause) {
                                // The re-authentication request has failed
                                // utterly. Running the original request again
                                // will ensure to trigger any error listeners
                                // attached to it.
                                clearRequestQueue();
                                volleyRequestQueue.start();
                                addToPriorityQueue((com.android.volley.Request<T>) originalRequest);
                                return false;
                            }

                        })
                        .withSessionListener(new SessionListener() {

                            @Override
                            @SuppressWarnings("unchecked")
                            public boolean onSessionChanged(String authToken, String refreshToken, long expires) {
                                // The authentication has succeeded and the new
                                // session tokens are now available.
                                addToPriorityQueue((com.android.volley.Request<T>) originalRequest);
                                volleyRequestQueue.start();
                                return false;
                            }

                        }));

                return true;
            }

        });

        volleyRequestQueue.add(request);

        return request;
    }

    public void setup(Context context, String scheme, String authority, String clientId, String clientSecret, SSLSocketFactory sslSocketFactory) {
        this.scheme = scheme;
        this.authority = authority;
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        // Ensure the expected request queues exists.
        if (sslSocketFactory == null) {
            if (volleyRequestQueue == null) {
                volleyRequestQueue = Volley.newRequestQueue(context);
                volleyRequestQueue.start();
            }

            if (volleyPriorityQueue == null) {
                volleyPriorityQueue = Volley.newRequestQueue(context);
                volleyPriorityQueue.start();
            }
        } else if (volleyRequestQueue == null || volleyPriorityQueue == null) {
            HurlStack stack = new HurlStack(null, sslSocketFactory);

            if (volleyRequestQueue == null) {
                volleyRequestQueue = Volley.newRequestQueue(context, stack);
                volleyRequestQueue.start();
            }

            if (volleyPriorityQueue == null) {
                volleyPriorityQueue = Volley.newRequestQueue(context, stack);
                volleyPriorityQueue.start();
            }
        }

        // Clear out any and all queued requests.
        // TODO: It's a bit unclear what will happen to any processing requests.
        RequestFilter blindRequestFilter = new RequestFilter() {

            @Override
            public boolean apply(com.android.volley.Request<?> request) {
                return true;
            }

        };

        volleyRequestQueue.cancelAll(blindRequestFilter);
        volleyPriorityQueue.cancelAll(blindRequestFilter);

        // Clear out any cached content in the request queues.
        Cache requestCache = volleyRequestQueue.getCache();
        if (requestCache != null) {
            requestCache.clear();
        }

        Cache priorityCache = volleyPriorityQueue.getCache();
        if (priorityCache != null) {
            priorityCache.clear();
        }
    }

    protected void addToRequestQueue(com.android.volley.Request<?> request) {
        if (request != null) {
            volleyRequestQueue.add(request);
        }
    }

    protected void addToPriorityQueue(com.android.volley.Request<?> request) {
        if (request != null) {
            volleyPriorityQueue.add(request);
        }
    }

    protected void clearRequestQueue() {
        volleyRequestQueue.cancelAll(new RequestFilter() {

            @Override
            public boolean apply(com.android.volley.Request<?> request) {
                return true;
            }

        });
    }

    protected HashMap<String, String> parseParams(Uri uri) {
        Set<String> keys = uri.getQueryParameterNames();
        HashMap<String, String> params = new HashMap<String, String>();

        for (String key : keys) {
            String value = uri.getQueryParameter(key);
            params.put(key, value);
        }

        return params;
    }

    protected String parseUrl(Uri uri) {
        if (Utils.isEmpty(uri)) {
            return null;
        }

        String url = uri.toString();
        int queryStart = url.indexOf("?");

        if (queryStart > 0) {
            url = url.substring(0, queryStart);
        }

        return url;
    }

    private Uri buildAuthUri() {
        Uri result = null;
        String refreshToken = Session.refreshToken();

        if (Utils.notEmpty(refreshToken)) {
            result = new AuthPath()
                    .withClientCredentials(clientId, clientSecret)
                    .withRefreshToken(refreshToken)
                    .buildUri(scheme, authority);
        }

        return result;
    }

}
