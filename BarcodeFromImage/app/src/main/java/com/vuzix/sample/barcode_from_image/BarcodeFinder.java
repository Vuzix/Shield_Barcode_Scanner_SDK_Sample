/*
 Copyright (c) 2018, Vuzix Corporation
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 Redistributions of source code must retain the above copyright
 notice, this list of conditions and the following disclaimer.

 Redistributions in binary form must reproduce the above copyright
 notice, this list of conditions and the following disclaimer in the
 documentation and/or other materials provided with the distribution.

 Neither the name of Vuzix Corporation nor the names of
 its contributors may be used to endorse or promote products derived
 from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.vuzix.sample.barcode_from_image;

import android.content.Context;
import android.media.Image;
import android.media.ImageReader;
import com.vuzix.sdk.barcode.ScanResult2;
import com.vuzix.sdk.barcode.Scanner2;
import com.vuzix.sdk.barcode.Scanner2Factory;

import java.nio.ByteBuffer;


/**
 * Utility class to find barcodes in images.
 *
 * This shows a real-world conversion of image data and how to call the barcode engine
 *
 * This is called from a worker thread, not the UI thread since it might take a noticeable amount
 * of time to analyze the image
 */

class BarcodeFinder {

    private Scanner2 mScanner=null;
    /**
     * Initialize the scan engine
     *
     * @note: Failure to do this will leave the engine in a demonstration mode, and scan data will not be usable.
     */
    public BarcodeFinder(Context iContext) {

        //Call into the SDK to create a scanner instance.
        try {
            mScanner = Scanner2Factory.getScanner(iContext);
        }catch (Exception ex){

        }
    }

    /**
     * Parses the image data to the barcode engine and displays the results
     */
    public String getBarcodeResults(ImageReader reader) {

        // get the latest image and convert to a bitmap
        Image image = reader.acquireNextImage(); // Use acquireNextImage() instead of acquireLatestImage() since we created the reader with a maxImages of 1
        ByteBuffer buffer = image.getPlanes()[0].getBuffer(); // Y component is all we need
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        // pass data into barcode scan engine
        ScanResult2[] results = mScanner.scan(data, image.getWidth(), image.getHeight(),null);

        // Examine the results
        String resultString = null;
        if (results!=null && results.length > 0) {
            resultString = results[0].getText();   // Use the first one, if any are available
        }
        return resultString;
    }

}
