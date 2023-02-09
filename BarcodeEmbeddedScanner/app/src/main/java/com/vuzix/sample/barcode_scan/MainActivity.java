/***************************************************************************************
Copyright (c) 2018, Vuzix Corporation
        All rights reserved.

        Redistribution and use in source and binary forms, with or without
        modification, are permitted provided that the following conditions
        are met:

        *  Redistributions of source code must retain the above copyright
        notice, this list of conditions and the following disclaimer.

        *  Redistributions in binary form must reproduce the above copyright
        notice, this list of conditions and the following disclaimer in the
        documentation and/or other materials provided with the distribution.

        *  Neither the name of Vuzix Corporation nor the names of
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
**************************************************************************************/

package com.vuzix.sample.barcode_scan;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.vuzix.sdk.barcode.ScanResult2;
import com.vuzix.sdk.barcode.ScannerFragment;
import com.vuzix.sdk.barcode.ScannerIntent;


import java.io.IOException;

/**
 * The main activity for the Vuzix M-Series barcode sample application
 */
public class MainActivity extends Activity implements PermissionsFragment.Listener {

    private static final String TAG_PERMISSIONS_FRAGMENT = "permissions";
    private static final int REQUEST_CODE_SCAN = 90001;
    private View scanInstructionsView;
    private ScannerFragment.Listener2 mScannerListener;

    /**
     * One-time initialization. Sets up the view and the permissions.
     * @param savedInstanceState - we have no saved state. Just pass through to superclass
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // This is a best practice on the  M-Series. Once the activity is started, the user will likely
        // look straight down to scan a barcode. Allow left and right eye operation, but lock it
        // in once started
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        // Since the Vuzix M400 API is level 23, always use runtime permissions
        PermissionsFragment permissionsFragment = (PermissionsFragment)getFragmentManager().findFragmentByTag(TAG_PERMISSIONS_FRAGMENT);
        if (permissionsFragment == null) {
            permissionsFragment = new PermissionsFragment();
            getFragmentManager().beginTransaction().add(permissionsFragment, TAG_PERMISSIONS_FRAGMENT).commit();
        }
        // Register as a PermissionsFragment.Listener so our permissionsGranted() is called
        permissionsFragment.setListener(this);

        // Hide the instructions until we have permission granted
        scanInstructionsView = findViewById(R.id.scan_instructions);
        scanInstructionsView.setVisibility(View.GONE);

        creeateScannerListener();
        //OnScanClick();
    }

    /**
     * Called upon permissions being granted. This is the only way we show the scanner with API 23
     */
    @Override
    public void permissionsGranted() {
        showScanner();
    }

    /**
     * Shows the scanner fragment in our activity
     */
    private void showScanner() {
        ScannerFragment scannerFragment = new ScannerFragment();
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, scannerFragment).commit();
        scannerFragment.setListener2(mScannerListener);                 // Required to get scan results
        scanInstructionsView.setVisibility(View.VISIBLE);  // Put the instructions back on the screen
    }

    private void creeateScannerListener() {
        try {
            /**
             * This is a simple wrapper class.
             *
             * We do this rather than having our MainActivity directly implement
             * ScannerFragment.Listener so we may gracefully catch the NoClassDefFoundError
             * if we are not running on an M-Series.
             */
            class OurScannerListener implements ScannerFragment.Listener2 {
                @Override
                public void onScan2Result(Bitmap bitmap, ScanResult2[] results) {
                    onScanFragmentScanResult(bitmap,results);
                }

                @Override
                public void onError() {
                    onScanFragmentError();
                }
            }

            mScannerListener = new OurScannerListener();

        } catch (NoClassDefFoundError e) {
            // We get this exception if the SDK stubs against which we compiled cannot be resolved
            // at runtime. This occurs if the code is not being run on an M400 supporting the voice
            // SDK
            Toast.makeText(this, R.string.only_on_mseries, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * This callback gives us the scan result.  This is relayed through mScannerListener.onScanResult
     *
     * This sample calls a helper class to display the result to the screen
     *
     * @param bitmap -  the bitmap in which barcodes were found
     * @param results -  an array of ScanResult
     */
    private void onScanFragmentScanResult(Bitmap bitmap, ScanResult2[] results) {
        ScannerFragment scannerFragment = (ScannerFragment)getFragmentManager().findFragmentById(R.id.fragment_container);
        scannerFragment.setListener2(null);
        showScanResult(bitmap, results[0]);
    }

    /**
     * This callback gives us scan errors. This is relayed through mScannerListener.onError
     *
     * At a minimum the scanner fragment must be removed from the activity. This sample closes
     * the entire activity, since it has no other functionality
     */
    private void onScanFragmentError() {
        finish();
        Toast.makeText(this, R.string.scanner_error_message, Toast.LENGTH_LONG).show();
    }

    /**
     * Helper method to show a scan result
     *
     * @param bitmap -  the bitmap in which barcodes were found
     * @param result -  an array of ScanResult
     */
    private void showScanResult(Bitmap bitmap, ScanResult2 result) {
        scanInstructionsView.setVisibility(View.GONE);
        ScanResultFragment scanResultFragment = new ScanResultFragment();
        Bundle args = new Bundle();
        args.putParcelable(ScanResultFragment.ARG_BITMAP, bitmap);
        args.putParcelable(ScanResultFragment.ARG_SCAN_RESULT, result);
        scanResultFragment.setArguments(args);
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, scanResultFragment).commit();
        beep();
    }

    /**
     * A best practice is to give some audible feedback during scan operations. This beeps.
     */
    private void beep() {
        MediaPlayer player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
            }
        });
        try {
            AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep);
            player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            file.close();
            player.setVolume(.1f, .1f);
            player.prepare();
            player.start();
        } catch (IOException e) {
            player.release();
        }
    }

    /**
     * Basic control to return from the result fragment to the scanner fragment, or exit the app from the scanner
     */
    @Override
    public void onBackPressed() {
        if (isScanResultShowing()) {
            showScanner();
            return;
        }
        super.onBackPressed();
    }

    /**
     * Utility to determine if the scanner result fragment is showing
     * @return True if showing
     */
    private boolean isScanResultShowing() {
        return getFragmentManager().findFragmentById(R.id.fragment_container) instanceof ScanResultFragment;
    }
}
