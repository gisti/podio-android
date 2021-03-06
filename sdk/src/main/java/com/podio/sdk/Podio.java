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

package com.podio.sdk;

import android.content.Context;

import com.podio.sdk.Request.ErrorListener;
import com.podio.sdk.Request.SessionListener;
import com.podio.sdk.provider.ApplicationProvider;
import com.podio.sdk.provider.CalendarProvider;
import com.podio.sdk.provider.ClientProvider;
import com.podio.sdk.provider.ContactProvider;
import com.podio.sdk.provider.ConversationProvider;
import com.podio.sdk.provider.ItemProvider;
import com.podio.sdk.provider.OrganizationProvider;
import com.podio.sdk.provider.StoreProvider;
import com.podio.sdk.provider.TaskProvider;
import com.podio.sdk.provider.UserProvider;
import com.podio.sdk.provider.ViewProvider;
import com.podio.sdk.push.FayePushClient;
import com.podio.sdk.push.PushClient;
import com.podio.sdk.push.VolleyLongPollingTransport;
import com.podio.sdk.volley.VolleyClient;
import com.podio.sdk.volley.VolleyRequest;

import javax.net.ssl.SSLSocketFactory;

/**
 * Enables easy access to the Podio API with a basic configuration which should be suitable for most
 * third party developers.
 *
 * @author László Urszuly
 */
public class Podio {
    private static final String DEFAULT_SCHEME = "https";
    private static final String DEFAULT_AUTHORITY_API = "api.podio.com";

    /**
     * The default request client for the providers.
     */
    protected static VolleyClient restClient = new VolleyClient();

    public static PushClient push;

    /**
     * Enables means of easy operating on the Application API end point.
     */
    public static final ApplicationProvider application = new ApplicationProvider();

    /**
     * Enables means of easy operating on the Calendar API end point.
     */
    public static final CalendarProvider calendar = new CalendarProvider();

    /**
     * Enables means of easy operating on the {@link ContactProvider} API end point.
     */
    public static final ContactProvider contact = new ContactProvider();

    /**
     * Enables means of easy operating on the Conversation API end point.
     */
    public static final ConversationProvider conversation = new ConversationProvider();

    /**
     * Enables means of easy authentication.
     */
    public static final ClientProvider client = new ClientProvider();

    /**
     * Enables means of easy operating on the Item API end point.
     */
    public static final ItemProvider item = new ItemProvider();

    /**
     * Enables means of easy operating on the Organization API end point.
     */
    public static final OrganizationProvider organization = new OrganizationProvider();

    /**
     * Enables means of requesting a handle to a local store manager.
     */
    public static final StoreProvider store = new StoreProvider();

    /**
     * Enables means of easy operating on the User API end point.
     */
    public static final UserProvider user = new UserProvider();

    /**
     * Enables means of easy operating on the View API end point.
     */
    public static final ViewProvider view = new ViewProvider();

    /**
     * Enables means of easy operating on the Task API end point.
     */
    public static final TaskProvider task = new TaskProvider();

    /**
     * Enables means of registering global error listeners. These callback implementations apply to
     * <em>all</em> requests until explicitly removed and they are called <em>after</em> any custom
     * callbacks added to a particular request future are called.
     * <p>
     * If a callback chooses to consume a given event, then <em>all</em> further bubbling is
     * aborted, meaning that the event may not reach the global event listener you add here.
     *
     * @param errorListener
     *         The global listener to register.
     *
     * @return The registered listener on success, null otherwise.
     */
    public static ErrorListener addGlobalErrorListener(ErrorListener errorListener) {
        return VolleyRequest.addGlobalErrorListener(errorListener);
    }

    /**
     * Registers a global session listeners. These callback implementations apply to <em>all</em>
     * requests until explicitly removed and they are called <em>after</em> any custom callbacks
     * added to a particular request future are called.
     * <p>
     * If a callback chooses to consume a given event, then <em>all</em> further bubbling is
     * aborted, meaning that the event may not reach the global event listener you add here.
     *
     * @param sessionListener
     *         The global listener to register.
     *
     * @return The registered listener on success, null otherwise.
     */
    public static SessionListener addGlobalSessionListener(SessionListener sessionListener) {
        return VolleyRequest.addGlobalSessionListener(sessionListener);
    }

    /**
     * Unregisters a previously registered global error listener.
     *
     * @param errorListener
     *         The listener to unregister.
     *
     * @return The listener that has just been unregistered, or null if the listener couldn't be
     * found.
     */
    public static ErrorListener removeGlobalErrorListener(ErrorListener errorListener) {
        return VolleyRequest.removeGlobalErrorListener(errorListener);
    }

    /**
     * Unregisters a previously registered global session listener.
     *
     * @param sessionListener
     *         The listener to unregister.
     *
     * @return The listener that has just been unregistered, or null if the listener couldn't be
     * found.
     */
    public static SessionListener removeGlobalSessionListener(SessionListener sessionListener) {
        return VolleyRequest.removeGlobalSessionListener(sessionListener);
    }

    /**
     * Initializes the Podio facade to it's default initial state.
     *
     * @param context
     *         The context to initialize the cache database and network clients in.
     * @param clientId
     *         The pre-shared Podio client id.
     * @param clientSecret
     *         The corresponding Podio client secret.
     *
     * @see Podio#setup(Context, String, String, String, String, SSLSocketFactory)
     */
    public static void setup(Context context, String clientId, String clientSecret) {
        setup(context, DEFAULT_SCHEME, DEFAULT_AUTHORITY_API, clientId, clientSecret, null);
    }

    /**
     * Initializes the Podio SDK with the given client credentials. This method MUST be called
     * before any other request is made.
     *
     * @param context
     *         The context to initialize the cache database and network clients in.
     * @param authority
     *         The host the SDK will target with its requests.
     * @param clientId
     *         The pre-shared Podio client id.
     * @param clientSecret
     *         The corresponding Podio client secret.
     * @param sslSocketFactory
     *         Optional custom SSL socket factory to use in the HTTP requests.
     */
    public static void setup(Context context, String scheme, String authority, String clientId, String clientSecret, SSLSocketFactory sslSocketFactory) {
        restClient.setup(context, scheme, authority, clientId, clientSecret, sslSocketFactory);

        // TODO: Enable proper configuration of push end point.
        String pushUrl = scheme + "://" + authority.replace("api.", "push.") + "/faye";
        push = new FayePushClient(new VolleyLongPollingTransport(context, pushUrl));

        // Providers relying on a rest client in order to operate properly.
        application.setClient(restClient);
        calendar.setClient(restClient);
        client.setClient(restClient);
        contact.setClient(restClient);
        conversation.setClient(restClient);
        item.setClient(restClient);
        organization.setClient(restClient);
        user.setClient(restClient);
        view.setClient(restClient);
        task.setClient(restClient);

        // Providers that doesn't need a rest client in order to operate.
        // store;
    }

    /**
     * Restores a previously created Podio session. Even though the access token may have expired,
     * the refresh token can be used to get a new access token. The idea here is to enable the
     * caller to persist the session and avoid an unnecessary re-authentication. NOTE! The server
     * may very well invalidate both the access and refresh tokens, which would require a
     * re-authentication anyway.
     *
     * @param accessToken
     *         The previously stored access token.
     * @param refreshToken
     *         The previously stored refresh token.
     * @param expires
     *         The previously stored expire time stamp (in seconds).
     */
    public static void restoreSession(String accessToken, String refreshToken, long expires) {
        Session.set(accessToken, refreshToken, expires);
    }

}
