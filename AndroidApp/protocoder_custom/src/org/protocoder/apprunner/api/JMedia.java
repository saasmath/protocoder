/*
 * Protocoder 
 * A prototyping platform for Android devices 
 * 
 * Victor Diaz Barrales victormdb@gmail.com
 *
 * Copyright (C) 2013 Motorola Mobility LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions: 
 * 
 * The above copyright notice and this permission notice shall be included in all 
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN 
 * THE SOFTWARE.
 * 
 */

package org.protocoder.apprunner.api;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import org.json.JSONArray;
import org.protocoder.apidoc.annotation.APIMethod;
import org.protocoder.apidoc.annotation.APIParam;
import org.protocoder.apprunner.AppRunnerActivity;
import org.protocoder.apprunner.AppRunnerSettings;
import org.protocoder.apprunner.JInterface;
import org.protocoder.apprunner.JavascriptInterface;
import org.protocoder.media.Audio;
import org.protocoder.media.AudioService;
import org.protocoder.sensors.WhatIsRunning;
import org.puredata.android.service.PdService;
import org.puredata.android.utils.PdUiDispatcher;
import org.puredata.core.PdBase;
import org.puredata.core.PdReceiver;
import org.puredata.core.utils.PdDispatcher;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaRecorder;
import android.speech.RecognizerIntent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class JMedia extends JInterface {

    String TAG = "JMedia";
    String onVoiceRecognitionfn;

    public JMedia(AppRunnerActivity a) {
	super(a);

	((AppRunnerActivity) a).addVoiceRecognitionListener(new onVoiceRecognitionListener() {

	    @Override
	    public void onNewResult(String text) {
		Log.d(TAG, "" + text);
		callback(onVoiceRecognitionfn, "\"" + text + "\"");
	    }

	});

    }

    // @JavascriptInterface
    // @APIAnnotation(description = "plays a video", example =
    // "media.playVieo(fileName);")
    public void playVideo(String file) {

    }

    @JavascriptInterface
    @APIMethod(description = "plays a sound", example = "media.playSound(fileName);")
    @APIParam(params = { "fileName" })
    public void playSound(String url) {

	if (url.startsWith("http://") == false) {
	    url = AppRunnerSettings.get().project.getFolder() + File.separator + url;
	}
	Audio.playSound(url, 100);
    }

    @JavascriptInterface
    @APIMethod(description = "", example = "")
    @APIParam(params = { "fileName", "function(objectType, value)" })
    public JPureData initPDPatch(String fileName, final String callbackfn) {
	String filePath = AppRunnerSettings.get().project.getFolder() + File.separator + fileName;

	PdReceiver receiver = new PdReceiver() {

	    @Override
	    public void print(String s) {
		Log.d(TAG, "pd >>" + s);
		callback(callbackfn, "print", s);
	    }

	    @Override
	    public void receiveBang(String source) {
		Log.d(TAG, "bang");
		callback(callbackfn, "bang", source);
	    }

	    @Override
	    public void receiveFloat(String source, float x) {
		Log.d(TAG, "float: " + x);
		callback(callbackfn, source, x);
	    }

	    @Override
	    public void receiveList(String source, Object... args) {
		Log.d(TAG, "list: " + Arrays.toString(args));

		JSONArray jsonArray = new JSONArray();
		for (int i = 0; i < args.length; i++) {
		    jsonArray.put(args[i]);
		}

		callback(callbackfn, source, jsonArray);
	    }

	    @Override
	    public void receiveMessage(String source, String symbol, Object... args) {
		Log.d(TAG, "message: " + Arrays.toString(args));
		callback(callbackfn, source, symbol);
	    }

	    @Override
	    public void receiveSymbol(String source, String symbol) {
		Log.d(TAG, "symbol: " + symbol);
		callback(callbackfn, source, symbol);
	    }

	    public void stop() {
		a.get().unbindService(AudioService.pdConnection);
	    }
	};

	// create and install the dispatcher
	PdDispatcher dispatcher = new PdUiDispatcher() {

	    @Override
	    public void print(String s) {
		Log.i("Pd print", s);
	    }

	};

	PdBase.setReceiver(dispatcher);

	// PdBase.setReceiver(receiver);
	PdBase.subscribe("android");
	// start pure data sound engine
	AudioService.file = filePath;
	Intent intent = new Intent((a.get()), PdService.class);
	// intent.putExtra("file", "qq.pd");

	(a.get()).bindService(intent, AudioService.pdConnection, (a.get()).BIND_AUTO_CREATE);
	initSystemServices();
	WhatIsRunning.getInstance().add(AudioService.pdConnection);

	return new JPureData();
    }

    private void initSystemServices() {
	TelephonyManager telephonyManager = (TelephonyManager) a.get().getSystemService(Context.TELEPHONY_SERVICE);
	telephonyManager.listen(new PhoneStateListener() {
	    @Override
	    public void onCallStateChanged(int state, String incomingNumber) {
		if (AudioService.pdService == null)
		    return;
		if (state == TelephonyManager.CALL_STATE_IDLE) {
		    AudioService.start();
		} else {
		    AudioService.pdService.stopAudio();
		}
	    }
	}, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @JavascriptInterface
    @APIMethod(description = "", example = "")
    @APIParam(params = { "fileName", "showProgressBoolean" })
    public void recordAudio(String fileName, boolean showProgress) {
	final MediaRecorder recorder = new MediaRecorder();
	// ContentValues values = new ContentValues(3);
	// values.put(MediaStore.MediaColumns.TITLE, fileName);
	recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
	recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
	recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

	recorder.setOutputFile(AppRunnerSettings.get().project.getFolder() + File.separator + fileName);
	try {
	    recorder.prepare();
	} catch (Exception e) {
	    e.printStackTrace();
	}

	final ProgressDialog mProgressDialog = new ProgressDialog(a.get());
	mProgressDialog.setTitle("Record!");
	mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
	mProgressDialog.setButton("Stop recording", new DialogInterface.OnClickListener() {
	    public void onClick(DialogInterface dialog, int whichButton) {
		mProgressDialog.dismiss();
		recorder.stop();
		recorder.reset();
		recorder.release();
	    }
	});

	mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
	    public void onCancel(DialogInterface p1) {
		recorder.stop();
		recorder.reset();
		recorder.release();
	    }
	});

	recorder.start();

	if (showProgress == true) {
	    mProgressDialog.show();
	}
    }

    @JavascriptInterface
    @APIMethod(description = "text to speech", example = "media.textToSpeech('hello world');")
    @APIParam(params = { "text" })
    public void textToSpeech(String text) {
	Audio.speak(a.get(), text, Locale.getDefault());
    }

    @JavascriptInterface
    @APIMethod(description = "start voice recognition", example = "media.startVoiceRecognition(function(text) { console.log(text) } );")
    @APIParam(params = { "function(recognizedText)" })
    public void startVoiceRecognition(final String callbackfn) {
	onVoiceRecognitionfn = callbackfn;

	Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
	intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
	intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell me something!");
	((AppRunnerActivity) a.get()).startActivityForResult(intent,
		((AppRunnerActivity) a.get()).VOICE_RECOGNITION_REQUEST_CODE);

    }

    public interface onVoiceRecognitionListener {
	public void onNewResult(String text);
    }

}
