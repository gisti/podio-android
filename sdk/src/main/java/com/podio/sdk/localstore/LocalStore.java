/*
 * Copyright (C) 2014 Copyright Citrix Systems, Inc. Permission is hereby
 * granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions: The above copyright notice and this
 * permission notice shall be included in all copies or substantial portions of
 * the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES
 * OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package com.podio.sdk.localstore;

import java.io.File;
import java.util.concurrent.Callable;

import android.content.Context;
import android.util.LruCache;

import com.podio.sdk.QueueClient;
import com.podio.sdk.Request;
import com.podio.sdk.Request.ResultListener;
import com.podio.sdk.Store;

/**
 * A {@link Store} implementation modeling a memory-cache backed by persistent
 * disk storage. The memory cache heavily relies on the Android {@link LruCache}
 * while the disk store is a basic directory in the internal cache directory of
 * the app. The actual contents are saved as JSON files in sub-directories.
 * <p>
 * The {@link Store} interface enables means of adding, removing, and fetching
 * content to and from the store. Further more the caller can choose to close
 * the store to free up memory, leaving the disk store intact, or even destroy
 * the store, wiping all data from both memory and disk store (but only for the
 * current store, though).
 * <p>
 * What is put in the store is completely up to the developer. There are no
 * constraints nor requirements on the data to have any association to Podio
 * domain objects. The only general requirement is for the <em>key</em> objects
 * to have a constant string representation (the disk store will call the
 * <code>toString()</code> method on them to determine which file to access) and
 * for the <code>value</code> objects to be able to be parsed by the Google Gson
 * library.
 * 
 * @author László Urszuly
 */
public class LocalStore extends QueueClient implements Store {

    /**
     * Erases all local stores in the root store folder for this app.
     * 
     * @param context
     *        The context used to find the cache directory for this app.
     */
    public static Request<Void> eraseAllDiskStores(Context context) {
        File root = LocalStoreRequest.getRootDirectory(context);
        EraseRequest request = LocalStoreRequest.newEraseRequest(null, root);
        LocalStore store = new LocalStore();
        store.execute(request);
        return request;
    }

    /**
     * Creates a new instance of this class and configures its initial state.
     * This is the only way to create and initialize a <code>LocalStore</code>.
     * 
     * @param context
     *        Used to fetch the disk storage folder.
     * @param name
     *        The name of the store.
     * @param maxMemoryInKiloBytes
     *        The memory size constraint.
     * @param listener
     *        The callback implementation through which the store will be
     *        delivered through.
     */
    public static Request<Store> open(Context context, String name, int maxMemoryInKiloBytes) {
        final LocalStore store = new LocalStore();

        InitMemoryRequest initMemoryStoreRequest = (InitMemoryRequest) LocalStoreRequest
                .newInitMemoryStoreRequest(maxMemoryInKiloBytes)
                .withResultListener(new ResultListener<LruCache<Object, Object>>() {

                    @Override
                    public boolean onRequestPerformed(LruCache<Object, Object> result) {
                        store.memoryStore = result;
                        return false;
                    }

                });

        InitDiskRequest initDiskStoreRequest = (InitDiskRequest) LocalStoreRequest
                .newInitDiskStoreRequest(context, name)
                .withResultListener(new ResultListener<File>() {

                    @Override
                    public boolean onRequestPerformed(File result) {
                        store.diskStore = result;
                        return false;
                    }

                });

        LocalStoreRequest<Store> deliverStoreRequest =
                new LocalStoreRequest<Store>(new Callable<Store>() {

                    @Override
                    public Store call() throws Exception {
                        return store;
                    }

                });

        // Since the thread pool executor is spawning one thread only and it's
        // backed by a linked blocking queue, it's fair to expect these requests
        // being addressed in a serialized manner, finishing with the deliver
        // request.
        store.execute(initMemoryStoreRequest);
        store.execute(initDiskStoreRequest);
        store.execute(deliverStoreRequest);

        return deliverStoreRequest;
    }

    /**
     * The in-memory store.
     */
    private LruCache<Object, Object> memoryStore;

    /**
     * The disk store directory.
     */
    private File diskStore;

    /**
     * Hidden constructor.
     */
    private LocalStore() {
        super(1, 1, 0L);
    }

    /**
     * Removes all objects in the memory cache. The disk store is left
     * unaffected.
     * 
     * @throws IllegalStateException
     *         If neither in-memory store, nor disk store has a valid handle.
     * @see com.podio.sdk.Store#destroy()
     */
    @Override
    public Request<Void> free() {
        FreeRequest request = LocalStoreRequest.newFreeRequest(memoryStore);
        execute(request);
        return request;
    }

    /**
     * Destroys this instance of the local store. The in memory cache will be
     * cleared and all files in the disk store, as well as the store container
     * itself, will be deleted.
     * 
     * @throws IllegalStateException
     *         If neither in-memory store, nor disk store has a valid handle.
     * @see com.podio.sdk.Store#destroy()
     */
    @Override
    public Request<Void> erase() {
        EraseRequest request = (EraseRequest) LocalStoreRequest
                .newEraseRequest(memoryStore, diskStore)
                .withResultListener(new ResultListener<Void>() {

                    @Override
                    public boolean onRequestPerformed(Void nothing) {
                        memoryStore = null;
                        diskStore = null;
                        return false; // Don't consume this event.
                    }

                });

        execute(request);
        return request;
    }

    /**
     * Retrieves an object with the given key from the local store. If the
     * object isn't found in memory, and a {@link Class} template is given, it
     * will be looked for on disk. If it's not found there either, a null
     * pointer will be returned.
     * 
     * @throws IllegalStateException
     *         If neither in-memory store, nor disk store has a valid handle.
     * @see com.podio.sdk.Store#get(java.lang.Object, java.lang.Class)
     */
    @Override
    public <T> Request<T> get(Object key, Class<T> classOfValue) throws IllegalStateException {
        GetRequest<T> request = LocalStoreRequest.newGetRequest(memoryStore, diskStore, key, classOfValue);
        execute(request);
        return request;
    }

    /**
     * Removes an object with the given key from the local store. If the removed
     * value was found in the memory store, then it will be returned, else if it
     * exists in the disk store and a {@link Class} template is given, the disk
     * store version will be returned prior to deletion.
     * 
     * @throws IllegalStateException
     *         If neither in-memory store, nor disk store has a valid handle.
     * @see com.podio.sdk.Store#remove(java.lang.Object, java.lang.Class)
     */
    @Override
    public Request<Void> remove(Object key) throws IllegalStateException {
        RemoveRequest request = LocalStoreRequest.newRemoveRequest(memoryStore, diskStore, key);
        execute(request);
        return request;
    }

    /**
     * Adds or updates a value with the given key in the local store. If there
     * already is a value for the given key in the store, it will silently be
     * overwritten.
     * 
     * @throws IllegalStateException
     *         If neither in-memory store, nor disk store has a valid handle.
     * @see com.podio.sdk.Store#put(java.lang.Object, java.lang.Object,
     *      java.lang.Class)
     */
    @Override
    public Request<Void> set(Object key, Object value) throws IllegalStateException {
        SetRequest request = LocalStoreRequest.newSetRequest(memoryStore, diskStore, key, value);
        execute(request);
        return request;
    }

}
