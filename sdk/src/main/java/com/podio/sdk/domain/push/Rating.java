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
package com.podio.sdk.domain.push;

import com.podio.sdk.internal.Utils;

abstract class Rating extends Event {

    private static class Data {
        /**
         * The reference to the object rated.
         */
        private final Reference data_ref = null;

        /**
         * The type of rating, e.g. "like"
         */
        private final String type = null;

        /**
         * The total number of grants on the object.
         */
        private final Integer count = null;
    }

    private final Data data = null;

    public String referenceType() {
        return data != null && data.data_ref != null ? data.data_ref.type() : null;
    }

    public long referenceId() {
        return data != null && data.data_ref != null ? data.data_ref.id() : -1L;
    }

    public int count() {
        return data != null ? Utils.getNative(data.count, -1) : -1;
    }

    public String ratingType() {
        return data != null ? data.type : null;
    }

}
